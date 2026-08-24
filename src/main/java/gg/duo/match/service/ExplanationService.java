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
import java.util.List;
import java.util.Optional;

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

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final double LOW_SCORE_THRESHOLD = 60.0;
    private static final String AGE_AXIS = "나이";

    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public ExplanationService(WebClient llmWebClient,
                              ObjectMapper objectMapper,
                              @Value("${llm.api.key}") String apiKey,
                              @Value("${llm.api.url}") String apiUrl,
                              @Value("${llm.model}") String model) {
        this.llmWebClient = llmWebClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
    }

    public ExplanationDto explain(TeamFitResult fit) {
        List<FitAxis> forAi = fit.axes().stream()
                .filter(a -> !AGE_AXIS.equals(a.axis()))
                .toList();

        if (apiKey == null || apiKey.isBlank() || "dummy_key".equals(apiKey)) {
            return fallback(forAi);
        }
        try {
            return callLlm(fit, forAi);
        } catch (Exception e) {
            log.warn("LLM 설명 생성 실패 — 규칙 기반 문구로 대체합니다.", e);
            return fallback(forAi);
        }
    }

    private ExplanationDto callLlm(TeamFitResult fit, List<FitAxis> forAi) {
        String prompt = """
                너는 게임 팀 매칭 결과를 해석한다.
                [규칙]
                - 아래 점수는 이미 계산된 값이다. 다시 계산하지 마라.
                - 숫자를 문장에 쓰지 마라.
                - 배열 앞쪽일수록 이 추천에 크게 기여한 항목이다.
                - 상위 2개를 근거로 reasons를 쓴다.
                - 60점 미만 항목이 있으면 caution 한 문장. 없으면 null.
                - "나이" 축은 언급하지 마라.
                - 존댓말, 각 문장 40자 이내.
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
        body.put("temperature", 0.4);

        JsonNode response = llmWebClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(TIMEOUT)
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

    private ExplanationDto fallback(List<FitAxis> forAi) {
        if (forAi.isEmpty()) {
            return new ExplanationDto("지금 조건에서 가장 궁합이 좋은 팀이에요.", List.of(), null);
        }
        FitAxis top = forAi.get(0);
        Optional<FitAxis> low = forAi.stream().filter(a -> a.score() < LOW_SCORE_THRESHOLD).findFirst();

        String headline = "%s이(가) 잘 맞는 팀이에요.".formatted(top.axis());
        String caution = low.map(a -> "%s은(는) 차이가 있어요.".formatted(a.axis())).orElse(null);

        return new ExplanationDto(headline, List.of(headline), caution);
    }
}
