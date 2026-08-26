package gg.duo.match.dto;

import java.util.List;

/**
 * /match/new "방 찾기" 화면에서 받는 조건. game 외 나머지는 전부 선택이며,
 * 값을 채운 항목만 Hard Filter로 적용된다 — "필터링에 맞지 않는 조건은 아예
 * 계산하거나 보여주지 않는다"(회의록). 이 조건들은 검색 시점에 사용자가 직접
 * 고른 "내가 찾는 방" 조건이라 완화 사다리 대상이 아니다 — 완화되는 건 기존
 * 마이크 호환성 체크(HardFilterService 참고)뿐이다.
 */
public record MatchSearchRequest(
        String game,                        // 필수. 예: "LOL", "VALORANT" (한글 라벨도 허용 — HardFilterService.GAME_ALIASES)
        String gameMode,                     // 선택. 없으면 모든 모드 대상. 게임마다 값 목록이 다르다(LOL: 랭크/칼바람, 발로란트: 경쟁전/데스매치 등) — 프론트가 게임별로 다른 옵션을 보여준다
        List<String> positions,              // 선택. 내가 채우고 싶은 포지션/역할(복수). 비어있으면 프로필의 position 하나로 대체. 게임마다 어휘가 다르다(LOL 포지션 vs 발로란트 역할)
        Boolean micRequired,                 // (구버전 호환용) 선택. true=마이크 필수인 방만, false=마이크 필요 없는 방만, null=상관없음. micLevel이 있으면 그쪽을 우선한다
        String micLevel,                     // 선택. REQUIRED | PREFERRED | ANY. micRequired보다 우선 적용된다(HardFilterService.matchesMicPreference)
        String tier,                         // 선택. 이 검색에서 내 티어로 취급할 값(게임별 서열표 문자열, 예: "골드"). 없으면 프로필의 tier/riotTier를 그대로 쓴다
        String playStyle,                    // 선택. 빡겜/즐겜 — post의 PostGameRequirement.playStyle(이 모집이 원하는 텐션)과 정확히 일치하는 글만 남기는 Hard Filter다(HardFilterService.matchesPlayStyle). null/빈값이면 상관없음 — 필터링하지 않는다.
        List<Integer> targetMembersOptions,  // 선택. 희망 파티원 수(본인 포함, 복수 선택 가능)
        String playTime,                     // 선택. 이번 검색에서 내 플레이 시간대로 취급할 값(콤마 구분, 프로필 playTimes와 같은 어휘: 아침/낮/저녁/새벽). 비우면 프로필 값을 쓴다. TeamFitCalculator의 "플레이 시간대" 축에 반영된다 — 예전엔 자유 텍스트("오늘 21시")라 상대 값과 비교할 방법이 없어 저장도 안 되고 점수에도 못 쓰였다.
        Integer limit                        // 선택. 기본 5, 최대 20
) {
    public int limitOrDefault() {
        if (limit == null || limit <= 0) return 5;
        return Math.min(limit, 20);
    }
}