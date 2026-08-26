package gg.duo.match.dto;

import java.util.List;

/**
 * post 서비스(지금은 모놀리스)의 PostDto.Summary + /internal/posts/party 에서
 * 매칭에 필요한 필드만 뽑은 DTO.
 *
 * authorPersonality/members는 post/user 서비스의 공개 API가 아니라 내부 전용
 * 엔드포인트(user의 /internal/users/personality, post의 /internal/posts/party)에서
 * 채워진다 — MatchSearchService가 후보 목록을 받은 뒤 별도로 조회해 이 DTO에
 * 합쳐 넣는다(PostClient/UserClient 참고). 성향 점수는 "남의 프로필 조회"로는
 * 볼 수 없어야 해서 공개 응답에 얹지 않기로 했기 때문이다.
 *
 * author* 필드들은 "파티의 첫 번째 구성원(방장)"의 프로필이다. 파티원(members)과
 * 같은 항목을 갖고 있어야 TeamFitCalculator 가 방장과 파티원을 구분 없이 한 줄로
 * 세워 계산할 수 있다 — 그래서 나이·성향뿐 아니라 시간대 3종도 여기 담는다.
 */
public record PostSummaryDto(
        Long id,
        String title,
        Long authorId,
        String authorNickname,
        String authorTier,       // author.tier (자기 신고)
        String authorRiotTier,   // author.riotTier (라이엇 연동, 있으면 이쪽을 우선)
        String authorPlayStyle,  // author.playStyle — 작성자 프로필의 평소 성향 카테고리. 아래 playStyle과는 다른 값이다.
        Integer authorAge,
        String authorPlayTimes,    // author.playTimes — 콤마 구분, 예: "저녁,새벽"
        String authorPlayDays,     // author.playDays — 콤마 구분, 예: "금,토,일"
        String authorPlayDuration, // author.playDuration — 예: "2~4시간"
        PersonalityProfile authorPersonality,
        String game,
        String gameMode,
        String playTime,
        boolean micRequired,
        String micLevel,         // REQUIRED | PREFERRED | ANY — post.voiceChat 그대로
        String roles,            // 콤마 구분, "찾는 포지션/역할". 게임별 어휘가 다르다(LOL 포지션 vs 발로란트 역할). post 응답 필드명과 통일
        String wantedTier,       // 모집 글이 찾는 티어 한 개(자기 신고). null/"상관없음"이면 티어 제한 없음 — 허용 폭은 GameMatchingStrategy가 정한다
        String playStyle,        // 이 모집이 원하는 텐션(빡겜/즐겜) — post.playStyle, PostGameRequirement에서 온다(PostDto 주석 참고). authorPlayStyle과 다르다: 이건 "이 글"이 찾는 값이고 authorPlayStyle은 "작성자 개인"의 평소 값이다. null이면 이 글은 텐션을 안 정한 것 — HardFilterService가 상관없음으로 취급한다.
        int targetMembers,
        long currentMembers,
        String status,           // RECRUITING | CLOSED
        List<PartyMemberDto> members  // 이미 참여 확정(CONFIRMED)된 파티원(작성자 제외)
) {
    /** PostClient가 만든 "반쪽" 인스턴스에 작성자/파티원 성향을 채운 새 인스턴스를 만든다. record는 불변이라 필드를 직접 못 바꾼다. */
    public PostSummaryDto withParty(PersonalityProfile authorPersonality, List<PartyMemberDto> members) {
        return new PostSummaryDto(id, title, authorId, authorNickname, authorTier, authorRiotTier,
                authorPlayStyle, authorAge, authorPlayTimes, authorPlayDays, authorPlayDuration,
                authorPersonality, game, gameMode, playTime, micRequired,
                micLevel, roles, wantedTier, playStyle, targetMembers, currentMembers, status, members);
    }
}
