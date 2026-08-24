package gg.duo.match.dto;

import java.util.List;

/**
 * post 서비스(지금은 모놀리스)의 PostDto.Summary에서 매칭에 필요한 필드만 뽑은 DTO.
 *
 * authorAge/authorPersonality/tierMin/tierMax/micLevel/members는 Team Fit v2
 * (회의록 "MVP/고도화 범위 결정") 설계로 새로 필요해진 필드다. post 서비스가 아직
 * 내려주지 않는 필드는 PostClient가 null/empty로 채우고, 그만큼 관련 Hard Filter는
 * "제한 없음"으로 취급한다 — 정보가 없다는 이유로 후보를 과도하게 걸러내지 않는다.
 */
public record PostSummaryDto(
        Long id,
        String title,
        Long authorId,
        String authorNickname,
        String authorTier,       // author.tier (자기 신고)
        String authorRiotTier,   // author.riotTier (라이엇 연동, 있으면 이쪽을 우선)
        String authorPlayStyle,  // author.playStyle
        Integer authorAge,
        PersonalityProfile authorPersonality,
        String game,
        String gameMode,
        String playTime,
        boolean micRequired,
        String micLevel,         // REQUIRED | PREFERRED | ANY — micRequired를 대체할 3단계 값. 없으면 micRequired로 대체 판단
        String positions,        // 콤마 구분, "찾는 포지션/역할". 게임별 어휘가 다르다(LOL 포지션 vs 발로란트 역할)
        String tierMin,          // 모집 희망 티어 하한. null/"상관없음"이면 하한 없음
        String tierMax,          // 모집 희망 티어 상한. null/"상관없음"이면 상한 없음
        int targetMembers,
        long currentMembers,
        String status,           // RECRUITING | CLOSED
        List<PartyMemberDto> members  // 이미 참여 확정된 파티원(작성자 제외). post 서비스가 아직 안 내려주면 빈 리스트
) {
}
