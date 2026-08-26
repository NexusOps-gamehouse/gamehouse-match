package gg.duo.match.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gg.duo.match.dto.ExplanationDto;
import gg.duo.match.dto.FitAxis;
import gg.duo.match.service.TeamFitCalculator.TeamFitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 심화 프로젝트 필수 요건 "AI 기능 1개 이상 적용"에 해당하는 부분.
 * 검색 결과 1위에 대해서만 headline/reasons/caution 구조의 추천 이유를 만든다
 * (전체 결과에 다 걸면 요청마다 LLM 호출이 N번 나가서 비용/지연이 커진다).
 *
 * 점수는 이미 백엔드(TeamFitCalculator)가 계산했다 — AI는 그걸 자연어로 해석만
 * 한다. 다시 계산하지 않고, 순위를 바꾸지 않고, 숫자를 그대로 노출하지 않는다.
 * "나이" 축은 프롬프트에도, Fallback의 주의 문구 판단에도 넣지 않는다(회의록
 * 프롬프트 규칙 "'나이' 축은 언급하지 마라"를 그대로 따른다) — 프론트에는 여전히
 * FitAxis 목록 전체(나이 포함)가 내려가므로 "세부 점수 UI"에는 문제가 없다.
 *
 * llm.api.key가 dummy_key(기본값)거나 호출/파싱이 실패하면 규칙 기반 문구로
 * 즉시 대체한다. 타임아웃 3초 — LLM은 부가 기능이지 필수 경로가 아니다.
 */
@Slf4j
@Service
public class ExplanationService {

    private static final double LOW_SCORE_THRESHOLD = 60.0;
    private static final String AGE_AXIS = "나이";

    private static final List<String> NO_TEMPERATURE_PREFIXES = List.of("gpt-5", "o1", "o3", "o4");

    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final Duration timeout;
    private final Double temperature;

    public ExplanationService(WebClient llmWebClient,
                              ObjectMapper objectMapper,
                              @Value("${llm.api.key}") String apiKey,
                              @Value("${llm.api.url}") String apiUrl,
                              @Value("${llm.model}") String model,
                              @Value("${llm.timeout-ms}") long timeoutMs,
                              @Value("${llm.temperature}") double temperature) {
        this.llmWebClient = llmWebClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        // 음수면 "보내지 마라"는 뜻. 모델이 안 받는 경우와 사용자가 끄고 싶은 경우를 같은 스위치로 쓴다.
        this.temperature = temperature < 0 || !supportsTemperature(model) ? null : temperature;
    }

    private static boolean supportsTemperature(String model) {
        if (model == null) return false;
        String m = model.trim().toLowerCase(Locale.ROOT);
        return NO_TEMPERATURE_PREFIXES.stream().noneMatch(m::startsWith);
    }

    public ExplanationDto explain(TeamFitResult fit) {
        List<FitAxis> forAi = fit.axes().stream()
                .filter(a -> !AGE_AXIS.equals(a.axis()))
                // 재본 적 없는 축(설문·프로필 미입력이라 중립값으로 채워진 자리)은 설명에서 뺀다.
                // 안 그러면 중립값 50점이 "60점 미만이면 주의 문구" 규칙에 걸려, 측정한 적도
                // 없는 항목을 두고 "플레이 시간대는 차이가 있어요"라고 말하게 된다.
                // FitAxis.known 주석 참고 — 모른다와 안 맞는다는 다른 말이다.
                .filter(FitAxis::known)
                .toList();

        if (apiKey == null || apiKey.isBlank() || "dummy_key".equals(apiKey)) {
            log.debug("LLM 키가 없어 규칙 기반 문구를 씁니다. (llm.api.key 미설정)");
            return fallback(forAi);
        }
        try {
            return callLlm(fit, forAi);
        } catch (Exception e) {
            // 키를 넣었는데도 여기로 오면 대부분 모델명·엔드포인트 문제다. 화면에는
            // fallback 문구가 조용히 나가므로 "AI 설명이 왜 늘 똑같지?"로만 보인다 —
            // 그래서 무엇으로 호출했는지까지 로그에 남긴다.
            log.warn("LLM 설명 생성 실패 — 규칙 기반 문구로 대체합니다. model={} url={}", model, apiUrl, e);
            return fallback(forAi);
        }
    }

    private ExplanationDto callLlm(TeamFitResult fit, List<FitAxis> forAi) {
        String prompt = """
                너는 게임 팀 매칭 결과를 해석해 사용자에게 설명한다.
                [규칙]
                - 아래 점수는 이미 계산된 값이다. 다시 계산하거나 순위를 바꾸지 마라.
                - 숫자를 문장에 쓰지 마라.
                - 배열 앞쪽일수록 이 추천에 크게 기여한 항목이다.
                - headline: 이 파티가 나와 맞는 지점을 한 문장으로 요약한다.
                - reasons: 서로 다른 축 2개를 각각 한 문장씩. headline과 같은 문장을
                  반복하지 마라. "축 이름이 잘 맞아요" 식으로 이름만 되뇌지 말고,
                  그 축이 실제 플레이에서 어떤 뜻인지를 풀어 써라.
                  (예: 소통 적극성이 높으면 "콜이 잘 오갈 거예요")
                - caution: 점수가 낮아 부딪힐 수 있는 축 하나를 짚고, 무엇이 다른지 말한다.
                  낮은 축이 없으면 null.
                - "나이" 축은 언급하지 마라.
                - 조사(은/는, 이/가, 와/과)를 축 이름의 받침에 맞게 정확히 써라.
                - 존댓말, 각 문장 40자 이내.
                [축이 뜻하는 것]
                승리 지향성=이기는 것에 얼마나 진심인가 · 소통 적극성=말을 얼마나 많이 하는가
                주도성=누가 방향을 잡는가(서로 반대여야 잘 맞는다) · 실수 관용도=실수에 얼마나 너그러운가
                플레이 집중도=게임에 집중하는가 잡담하는가 · 친목 성향=계속 같이 할 사이를 원하는가
                플레이 시간대=접속 요일·시간이 겹치는가 · 음성 채팅=마이크 조건이 맞는가
                [해석 기준]
                90~100 매우 잘 맞음 · 75~89 잘 맞음 · 60~74 무난 · 60 미만 차이가 있음
                [데이터]
                %s
                [출력]
                {"headline": "", "reasons": [], "caution": null}
                """.formatted(toAiInputJson(fit, forAi));

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", model);
        var messages = body.putArray("messages");
        var msg = JsonNodeFactory.instance.objectNode();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);
        // 모델이 받는 경우에만 싣는다 — NO_TEMPERATURE_PREFIXES 주석 참고.
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        JsonNode response = llmWebClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .block();

        String content = response == null ? null
                : response.path("choices").path(0).path("message").path("content").asText(null);

        return parseOrFallback(content, forAi);
    }

    private String toAiInputJson(TeamFitResult fit, List<FitAxis> forAi) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("totalScore", fit.total());
        root.put("partySize", fit.partySize());
        ArrayNode axesNode = root.putArray("axes");
        for (FitAxis a : forAi) {
            ObjectNode axisNode = JsonNodeFactory.instance.objectNode();
            axisNode.put("axis", a.axis());
            axisNode.put("score", a.score());
            axisNode.put("weight", a.weight());
            axisNode.put("contribution", a.contribution());
            axesNode.add(axisNode);
        }
        return root.toString();
    }

    private ExplanationDto parseOrFallback(String content, List<FitAxis> forAi) {
        if (content == null || content.isBlank()) return fallback(forAi);
        try {
            return objectMapper.readValue(stripCodeFence(content.trim()), ExplanationDto.class);
        } catch (Exception e) {
            log.warn("LLM 응답 JSON 파싱 실패 — 규칙 기반 문구로 대체합니다. content={}", content, e);
            return fallback(forAi);
        }
    }

    /** LLM이 가끔 ```json ... ``` 코드펜스로 감싸서 응답하는 경우를 방어한다. */
    private String stripCodeFence(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            s = firstNewline >= 0 ? s.substring(firstNewline + 1) : s;
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    /**
     * 세 가지를 지킨다.
     *   - 조사는 받침을 보고 고른다.
     *   - headline과 reasons가 서로 다른 문장이고, reasons는 축이 실제로 뜻하는 바를 말한다.
     *   - caution은 "가장 많이 깎아먹은 축"(배점 × 부족분)을 집는다.
     */
    private ExplanationDto fallback(List<FitAxis> forAi) {
        if (forAi.isEmpty()) {
            return new ExplanationDto("아직 서로를 알 만한 정보가 부족해요.",
                    List.of("설문과 프로필을 채우면 훨씬 정확해져요."), null);
        }

        List<FitAxis> strong = forAi.stream()
                .filter(a -> a.score() >= LOW_SCORE_THRESHOLD)
                .limit(2)
                .toList();

        String headline = strong.isEmpty()
                ? "조건은 맞지만 성향은 꽤 다른 팀이에요."
                : strong.size() == 1
                    ? "%s%s 잘 맞는 팀이에요.".formatted(strong.get(0).axis(), particle(strong.get(0).axis(), "이", "가"))
                    : "%s%s %s%s 잘 맞는 팀이에요.".formatted(
                            strong.get(0).axis(), particle(strong.get(0).axis(), "과", "와"),
                            strong.get(1).axis(), particle(strong.get(1).axis(), "이", "가"));

        List<String> reasons = strong.isEmpty()
                ? List.of("겹치는 조건으로 묶인 파티예요.")
                : strong.stream().map(this::reasonOf).toList();

        // 가장 많이 깎아먹은 축 = 배점 × 부족분. 점수만 보면 배점 작은 축이 먼저 잡힌다.
        String caution = forAi.stream()
                .filter(a -> a.score() < LOW_SCORE_THRESHOLD)
                .max(Comparator.comparingDouble(a -> a.weight() * (100 - a.score()) / 100))
                .map(a -> "%s%s %s".formatted(a.axis(), particle(a.axis(), "은", "는"), gapPhrase(a.score())))
                .orElse(null);

        return new ExplanationDto(headline, reasons, caution);
    }

    /** 축이 실제로 뜻하는 바를 한 문장으로. 이름만 되뇌면 "높은 게 높다"는 말밖에 안 된다. */
    private String reasonOf(FitAxis a) {
        String band = a.score() >= 90 ? "아주 잘 맞아요." : a.score() >= 75 ? "잘 맞아요." : "무난해요.";
        String meaning = switch (a.axis()) {
            case "승리 지향성" -> "이기려는 마음가짐이";
            case "소통 적극성" -> "말이 오가는 정도가";
            case "주도성" -> "누가 이끌지가";
            case "실수 관용도" -> "실수를 넘기는 정도가";
            case "플레이 집중도" -> "게임에 집중하는 정도가";
            case "친목 성향" -> "관계를 이어가려는 마음이";
            case "플레이 시간대" -> "접속하는 요일과 시간이";
            case "음성 채팅" -> "마이크 조건이";
            default -> a.axis() + "이(가)";
        };
        return "%s %s".formatted(meaning, band);
    }

    private String gapPhrase(double score) {
        return score < 25 ? "서로 많이 달라요." : score < 45 ? "차이가 큰 편이에요." : "조금 차이가 있어요.";
    }

    /**
     * 한글 조사 선택. 마지막 글자에 받침이 있으면 withJong, 없으면 withoutJong.
     *
     * 한글 음절은 0xAC00부터 (초성×21 + 중성)×28 + 종성 으로 배열돼 있어서,
     * 28로 나눈 나머지가 0이 아니면 종성(받침)이 있다.
     * 예) "성"은 받침이 있어 "은/이/과", "시간대"는 없어서 "는/가/와".
     */
    private String particle(String word, String withJong, String withoutJong) {
        if (word == null || word.isBlank()) return withoutJong;
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return withoutJong; // 한글이 아니면 기본형
        return (last - 0xAC00) % 28 != 0 ? withJong : withoutJong;
    }
}
