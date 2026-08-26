package gg.duo.match.client;

import com.fasterxml.jackson.databind.JsonNode;
import gg.duo.match.dto.PostSummaryDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * post 서비스의 GET /api/posts 를 호출해 모집 중인 글 후보군을 가져온다.
 * 이 엔드포인트는 이미 game/gameMode/status 필터와 페이징을 지원하므로,
 * match-service는 별도의 신규 엔드포인트 없이 그대로 재사용한다.
 *
 * 여기서 만드는 PostSummaryDto는 author/members의 personality가 비어 있는
 * "반쪽"이다 — 이 응답(공개 API)에는 성향 점수가 없기 때문이다(PostSummaryDto
 * 주석 참고). authorPersonality·members는 MatchSearchService가 이후 단계에서
 * PostPartyClient(/internal/posts/party)와 UserClient(/internal/users/personality)
 * 조회 결과를 합쳐 채운다.
 *
 * 필드 이름은 post 응답의 실제 키와 맞춰 그대로 옮긴다(예전엔 post에 없는
 * positions/tierMin/tierMax/micLevel 키를 찾다가 항상 null이 되는 버그가 있었다) —
 * post가 실제로 내려주는 건 roles(콤마 구분)·tier(단일 값)·voiceChat(ANY/PREFERRED/
 * REQUIRED)·playStyle(빡겜/즐겜, PostGameRequirement 기반)이다.
 */
@Component
public class PostClient {

    private static final int CANDIDATE_POOL_SIZE = 100;

    private final WebClient postServiceWebClient;

    public PostClient(@Qualifier("postServiceWebClient") WebClient postServiceWebClient) {
        this.postServiceWebClient = postServiceWebClient;
    }

    public List<PostSummaryDto> listRecruiting(String game, String gameMode, String authorizationHeader) {
        JsonNode body = postServiceWebClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path("/api/posts")
                            .queryParam("game", game)
                            .queryParam("status", "RECRUITING")
                            .queryParam("page", 0)
                            .queryParam("size", CANDIDATE_POOL_SIZE);
                    if (gameMode != null && !gameMode.isBlank()) {
                        b.queryParam("gameMode", gameMode);
                    }
                    return b.build();
                })
                .header("Authorization", authorizationHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<PostSummaryDto> result = new ArrayList<>();
        if (body == null || !body.has("items")) return result;

        for (JsonNode p : body.get("items")) {
            JsonNode author = p.path("author");
            result.add(new PostSummaryDto(
                    p.path("id").asLong(),
                    p.path("title").asText(null),
                    author.path("id").asLong(),
                    author.path("nickname").asText(null),
                    author.path("tier").asText(null),
                    author.path("riotTier").asText(null),
                    author.path("playStyle").asText(null),
                    JsonParsingUtils.intOrNull(author, "age"),
                    // 방장의 시간대 3종. UserDto 에 이미 실려 오는 값인데 여기서 안 읽고
                    // 있어서 "플레이 시간대" 축이 방장 쪽을 통째로 모르는 상태가 된다.
                    author.path("playTimes").asText(null),
                    author.path("playDays").asText(null),
                    author.path("playDuration").asText(null),
                    null, // authorPersonality — MatchSearchService가 나중에 채운다
                    p.path("game").asText(null),
                    p.path("gameMode").asText(null),
                    p.path("playTime").asText(null),
                    micRequiredOf(p),
                    p.path("voiceChat").asText(null), // ANY | PREFERRED | REQUIRED — post의 실제 필드명
                    p.path("roles").asText(null),      // post의 실제 필드명(과거엔 없는 "positions"를 찾고 있었다)
                    p.path("tier").asText(null),        // 모집 글이 찾는 티어 한 개(과거엔 없는 tierMin/tierMax를 찾고 있었다)
                    p.path("playStyle").asText(null),   // 이 글이 원하는 텐션(빡겜/즐겜) — post/PostDto.Summary에 이미 있었는데 여기서 안 읽고 있었다. HardFilterService.matchesPlayStyle 참고
                    p.path("targetMembers").asInt(0),
                    p.path("currentMembers").asLong(0),
                    p.path("status").asText(null),
                    List.of() // members — MatchSearchService가 나중에 채운다
            ));
        }
        return result;
    }

    /** voiceChat이 REQUIRED일 때만 마이크 필수. 예전 micRequired(boolean) 대체값으로도 쓴다. */
    private boolean micRequiredOf(JsonNode post) {
        return "REQUIRED".equalsIgnoreCase(post.path("voiceChat").asText(""));
    }
}