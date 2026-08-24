package gg.duo.match.client;

import com.fasterxml.jackson.databind.JsonNode;
import gg.duo.match.dto.UserSummaryDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * user 서비스의 GET /api/users/{id} 를 호출한다.
 *
 * 이 엔드포인트는 로그인 필요라서, match-service가 대신 호출할 때도 원래 요청의
 * Authorization 헤더를 그대로 실어 보낸다(토큰 릴레이).
 *
 * age/playDays/playDuration/personality는 /signup/survey 개편으로 예정된 필드다.
 * common.dto.UserDto(user 서비스가 실제로 내려주는 형태)에는 아직 없어서 전부 null로
 * 채워지고, TeamFitCalculator는 그걸 중립값으로 취급한다 — user 서비스가 이 필드들을
 * 채워 내려주기 시작해도, 아직이어도 서로 깨지지 않는다.
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
                JsonParsingUtils.parsePersonality(body.path("personality"))
        );
    }
}
