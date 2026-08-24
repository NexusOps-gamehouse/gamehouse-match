package gg.duo.match.dto;

/**
 * user 서비스(지금은 모놀리스)의 UserDto에서 매칭에 필요한 필드만 뽑은 클라이언트 측 DTO.
 * match-service는 User 엔티티를 직접 참조하지 않는다 — DB도 다르고, 서비스도 다르다.
 *
 * age/playDays/playDuration/personality는 /signup/survey 개편(설문 대격변)으로
 * 새로 생긴 필드다. user 서비스가 아직 안 내려주면 UserClient가 null/empty로 채우고,
 * TeamFitCalculator는 그 경우 중립값으로 계산한다 — 필드가 없다고 매칭 자체가
 * 실패하지 않는다.
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
}
