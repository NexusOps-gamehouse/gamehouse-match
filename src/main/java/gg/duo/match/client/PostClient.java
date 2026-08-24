package gg.duo.match.client;

import com.fasterxml.jackson.databind.JsonNode;
import gg.duo.match.dto.PartyMemberDto;
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
 * tierMin/tierMax/micLevel/members/authorAge/authorPersonality는 Team Fit v2로
 * 필요해진 필드다. post 서비스(PostDto.Summary)가 아직 안 내려주면 null/빈 배열로
 * 채워지고, HardFilterService/TeamFitCalculator는 그만큼 "제한 없음"·"중립"으로 처리한다.
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
                    JsonParsingUtils.parsePersonality(author.path("personality")),
                    p.path("game").asText(null),
                    p.path("gameMode").asText(null),
                    p.path("playTime").asText(null),
                    p.path("micRequired").asBoolean(false),
                    p.path("micLevel").asText(null),
                    p.path("positions").asText(null),
                    p.path("tierMin").asText(null),
                    p.path("tierMax").asText(null),
                    p.path("targetMembers").asInt(0),
                    p.path("currentMembers").asLong(0),
                    p.path("status").asText(null),
                    parseMembers(p.path("members"))
            ));
        }
        return result;
    }

    /** post 서비스가 아직 파티원 배열을 안 내려주면 빈 리스트 — TeamFitCalculator는 작성자 한 명으로 계산한다. */
    private List<PartyMemberDto> parseMembers(JsonNode membersNode) {
        List<PartyMemberDto> members = new ArrayList<>();
        if (membersNode == null || !membersNode.isArray()) return members;
        for (JsonNode m : membersNode) {
            members.add(new PartyMemberDto(
                    m.path("id").asLong(),
                    m.path("nickname").asText(null),
                    JsonParsingUtils.intOrNull(m, "age"),
                    JsonParsingUtils.parsePersonality(m.path("personality"))
            ));
        }
        return members;
    }
}
