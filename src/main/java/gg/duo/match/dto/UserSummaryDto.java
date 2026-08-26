package gg.duo.match.dto;

/**
 * user 서비스(지금은 모놀리스)의 UserDto에서 매칭에 필요한 필드만 뽑은 클라이언트 측 DTO.
 * match-service는 User 엔티티를 직접 참조하지 않는다 — DB도 다르고, 서비스도 다르다.
 *
 * personality는 공개 GET /api/users/{id} 응답에는 없다(성향 점수를 "남의 프로필
 * 조회"로 노출하지 않기로 했다 — common.dto.UserDto 주석 참고). UserClient.getUser()가
 * 만드는 인스턴스는 이 필드가 항상 null이고, MatchSearchService가 내부 전용
 * /internal/users/personality 조회 결과를 withPersonality()로 나중에 채운다.
 * 그래도 값이 없으면(설문 미응답) TeamFitCalculator가 중립값으로 계산한다.
 */
public record UserSummaryDto(
        Long id,
        String nickname,
        String game,
        String position,
        String playStyle,
        String tier,
        String riotTier,
        String playTimes,
        boolean mic,
        Integer age,
        String playDays,          // 콤마 구분 요일, 예: "금,토,일"
        String playDuration,      // 1회 플레이 선호 시간 범위 코드, 예: "1~2시간"
        PersonalityProfile personality
) {
    public UserSummaryDto withPersonality(PersonalityProfile personality) {
        return new UserSummaryDto(id, nickname, game, position, playStyle, tier, riotTier,
                playTimes, mic, age, playDays, playDuration, personality);
    }

    /**
     * 검색 시점에 고른 시간대로 프로필 값을 덮어쓴 인스턴스를 만든다.
     * 비어 있으면 프로필 값을 그대로 둔다 — MatchSearchRequest.playTime 참고.
     */
    public UserSummaryDto withPlayTimesOverride(String override) {
        if (override == null || override.isBlank()) return this;
        return new UserSummaryDto(id, nickname, game, position, playStyle, tier, riotTier,
                override.trim(), mic, age, playDays, playDuration, personality);
    }
}
