package gg.duo.match.client;

import com.fasterxml.jackson.databind.JsonNode;
import gg.duo.match.dto.PersonalityProfile;
import gg.duo.match.dto.UserSummaryDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * user 서비스 조회. 두 갈래로 나뉜다.
 *   - GET /api/users/{id} (공개, 로그인 필요) : "나(me)"의 기본 프로필. 원래 요청의
 *     Authorization 헤더를 그대로 실어 보낸다(토큰 릴레이).
 *   - GET /internal/users, /internal/users/personality (내부 전용, 인증 불필요) :
 *     작성자·파티원의 기본 정보/성향 점수를 묶어서 가져온다. 성향 점수는
 *     "남의 프로필 조회"로 노출하면 안 되는 값이라 공개 엔드포인트에는 없다
 *     (common.dto.UserDto 주석 참고) — 그래서 personality만 별도 내부 API로 받는다.
 */
@Component
public class UserClient {

    private final WebClient userServiceWebClient;

    public UserClient(@Qualifier("userServiceWebClient") WebClient userServiceWebClient) {
        this.userServiceWebClient = userServiceWebClient;
    }

    public UserSummaryDto getUser(Long userId, String authorizationHeader) {
        JsonNode body = userServiceWebClient.get()
                .uri("/api/users/{id}", userId)
                .header("Authorization", authorizationHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (body == null) {
            throw new IllegalStateException("사용자 정보를 가져오지 못했습니다.");
        }

        return new UserSummaryDto(
                body.path("id").asLong(),
                body.path("nickname").asText(null),
                body.path("game").asText(null),
                body.path("position").asText(null),
                body.path("playStyle").asText(null),
                body.path("tier").asText(null),
                body.path("riotTier").asText(null),
                body.path("playTimes").asText(null),
                body.path("mic").asBoolean(false),
                JsonParsingUtils.intOrNull(body, "age"),
                body.path("playDays").asText(null),
                body.path("playDuration").asText(null),
                null // personality — MatchSearchService가 fetchPersonalities() 결과로 채운다
        );
    }

    /** id 묶음 → 닉네임/나이 등 기본 정보. 파티원(members) 표시용 — 성향은 안 담는다. */
    public Map<Long, JsonNode> fetchUsersByIds(List<Long> ids) {
        Map<Long, JsonNode> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) return result;

        JsonNode body = userServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/users")
                        .queryParam("ids", ids)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (body == null || !body.isArray()) return result;
        for (JsonNode u : body) result.put(u.path("id").asLong(), u);
        return result;
    }

    /** id 묶음 → 성향 점수. 설문에 응답한 적 없는 사용자는 결과 Map에서 빠진다(호출부가 null로 취급). */
    public Map<Long, PersonalityProfile> fetchPersonalities(List<Long> ids) {
        Map<Long, PersonalityProfile> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) return result;

        JsonNode body = userServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/users/personality")
                        .queryParam("ids", ids)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (body == null || !body.isArray()) return result;
        for (JsonNode entry : body) {
            PersonalityProfile p = JsonParsingUtils.parsePersonality(entry.path("personality"));
            if (p != null) result.put(entry.path("userId").asLong(), p);
        }
        return result;
    }
}