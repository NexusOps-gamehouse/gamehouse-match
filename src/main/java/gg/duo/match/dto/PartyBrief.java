package gg.duo.match.dto;

/**
 * 응답에 실리는 파티 구성원 한 명 — "누구와 하는 파티인가"를 화면에 보여주기 위한
 * 최소 정보다.
 *
 * PartyMemberDto 와 나눠 둔 이유: 그쪽은 계산용이라 성향 점수(personality)를 들고
 * 있는데, 그 값은 /internal 로만 내려오는 비공개 값이라 클라이언트로 나가면 안 된다.
 * 나이·닉네임은 이미 공개 프로필(/api/users/{id})에서 볼 수 있는 값이라 그대로 싣는다.
 *
 * 이 조회 자체는 원래도 하고 있었다(성향을 가져오려면 파티원 id 가 필요하다).
 * 값을 받아만 놓고 응답에 넣지 않아서 화면이 "OO님의 파티"라고만 말하고 실제로
 * 누가 있는지는 못 보여주던 상태였다.
 */
public record PartyBrief(
        Long userId,
        String nickname,
        Integer age,
        boolean host,       // 방장(모집글 작성자)인가
        boolean surveyed    // 성향 설문을 마친 사람인가 — 점수의 신뢰도를 화면에서 설명할 때 쓴다
) {
}
