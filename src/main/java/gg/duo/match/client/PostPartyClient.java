package gg.duo.match.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * post 서비스의 GET /internal/posts/party 를 호출해 글별 확정(CONFIRMED) 파티원
 * id 목록을 가져온다.
 *
 * 공개 GET /api/posts(PostClient)에는 이 정보가 없다 — "이 글에 누가 확정으로
 * 들어와 있는가"는 지금도 post 서비스에서 방장만 볼 수 있는 정보라서(방장 전용
 * 신청자 목록 API), 매칭 계산을 위해서라도 공개 응답에 새로 얹지 않고 내부
 * 전용 경로로만 가져온다. Authorization 헤더를 릴레이하지 않는 이유도 같다 —
 * /internal/** 은 사용자 인증이 아니라 네트워크 위치(Ingress 미노출)로 막혀 있다.
 */
@Component
public class PostPartyClient {

    private final WebClient postServiceWebClient;

    public PostPartyClient(@Qualifier("postServiceWebClient") WebClient postServiceWebClient) {
        this.postServiceWebClient = postServiceWebClient;
    }

    /** postId → 확정 파티원 user id 목록. 실패하거나 빈 입력이면 빈 Map — 그만큼 파티는 작성자 한 명으로 계산된다. */
    public Map<Long, List<Long>> fetchPartyMembers(List<Long> postIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) return result;

        JsonNode body = postServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/posts/party")
                        .queryParam("ids", postIds)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (body == null || !body.isArray()) return result;
        for (JsonNode entry : body) {
            List<Long> memberIds = new ArrayList<>();
            for (JsonNode idNode : entry.path("memberIds")) memberIds.add(idNode.asLong());
            result.put(entry.path("postId").asLong(), memberIds);
        }
        return result;
    }
}
