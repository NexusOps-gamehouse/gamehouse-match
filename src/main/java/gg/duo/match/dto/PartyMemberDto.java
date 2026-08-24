package gg.duo.match.dto;

/**
 * 모집글에 이미 참여 확정된 파티원 한 명. post 서비스가 아직 이 배열을 내려주지
 * 않으므로(README 참고), PostClient는 이 필드를 빈 리스트로 채운다 — 그 상태에서는
 * TeamFitCalculator가 작성자(author) 한 명만으로 "파티"를 구성해 계산한다.
 * post 서비스가 실제 파티원 목록을 내려주기 시작하면 match-service 코드 변경 없이
 * 그대로 다인원 평균 계산으로 반영된다.
 */
public record PartyMemberDto(
        Long userId,
        String nickname,
        Integer age,
        PersonalityProfile personality
) {
}
