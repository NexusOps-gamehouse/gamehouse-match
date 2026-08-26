package gg.duo.match.dto;

/**
 * 모집글에 이미 참여 확정(CONFIRMED)된 파티원 한 명.
 *
 * post 서비스의 /internal/posts/party 가 id 목록을 주고, user 서비스의
 * /internal/users(기본 정보) + /internal/users/personality(성향)가 나머지를 채운다
 * — 조립은 MatchSearchService.enrich() 가 한다. (예전 주석은 "post 가 아직 파티원을
 * 안 내려준다"고 되어 있었지만 그 경로는 이미 동작한다.)
 *
 * personality 는 계산 전용이라 응답으로 내보내지 않는다 — FitItem 에는 닉네임·나이만
 * 담은 PartyBrief 가 실린다. 성향 점수는 "남의 프로필 조회"로 볼 수 있는 값이 아니라
 * 애초에 /internal 로만 내려온다.
 */
public record PartyMemberDto(
        Long userId,
        String nickname,
        Integer age,
        String playTimes,      // 콤마 구분, 예: "저녁,새벽"
        String playDays,       // 콤마 구분, 예: "금,토,일"
        String playDuration,   // 1회 플레이 선호 분량, 예: "2~4시간"
        PersonalityProfile personality
) {
}
