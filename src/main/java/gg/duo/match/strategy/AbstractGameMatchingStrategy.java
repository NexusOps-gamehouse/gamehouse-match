package gg.duo.match.strategy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LOL/발로란트 두 구현체가 공유하는 로직. 게임마다 다른 건 어휘 세 개뿐이라
 * tierOrder() / tierAliases() / positions() 만 하위 클래스가 채우면 된다.
 */
abstract class AbstractGameMatchingStrategy implements GameMatchingStrategy {

    /**
     * 티어 허용 폭. 모집 글의 wantedTier(단일 값) 기준 ±1단계까지 통과시킨다.
     *
     * 실사용 데이터가 없는 상태에서 잡은 1차 값이다(TeamFitCalculator의 다른
     * 정규화 상수들과 같은 사정) — 나중에 게임별로 다르게 둘 수도 있다.
     */
    private static final int TIER_TOLERANCE = 1;

    /**
     * 하위 티어 → 상위 티어 순서.
     *
     * post 서비스 GameOptions.TIERS 와 **문자열까지 동일해야 한다**. 모집글에
     * 저장될 수 있는 값이 거기서 검증되기 때문에, 여기 목록이 다르면 그 티어를
     * 건 글은 필터에서 조용히 "티어 무관"으로 빠져나간다.
     */
    protected abstract List<String> tierOrder();

    /**
     * 서열표 밖의 표기 → 서열표 값. 라이엇 영문 enum, 옛 표기, 세분화된 상위 티어를
     * 흡수한다. 키는 대문자로 정규화해서 비교하므로 여기서도 대문자로 적는다.
     */
    protected abstract Map<String, String> tierAliases();

    /**
     * 이 게임의 포지션/역할 어휘. post 서비스 GameOptions.ROLES 와 동일해야 한다.
     */
    protected abstract List<String> positions();

    @Override
    public boolean positionMatches(List<String> searchPositions, String postWantedPositionsCsv) {
        if (isAnyOrBlank(postWantedPositionsCsv)) return true; // 모집글이 포지션 무관
        if (searchPositions == null || searchPositions.isEmpty()) return true; // 내가 포지션을 특정하지 않음

        List<String> wanted = splitCsv(postWantedPositionsCsv);
        return searchPositions.stream()
                .filter(p -> p != null && !p.isBlank())
                .anyMatch(p -> wanted.stream().anyMatch(w -> w.equalsIgnoreCase(p.trim())));
    }

    @Override
    public boolean knowsPosition(String position) {
        if (position == null || position.isBlank()) return false;
        String trimmed = position.trim();
        return positions().stream().anyMatch(p -> p.equalsIgnoreCase(trimmed));
    }

    @Override
    public String normalizeTier(String rawTier) {
        if (rawTier == null || rawTier.isBlank()) return null;
        String trimmed = rawTier.trim();

        // 1) 서열표에 그대로 있는 값 (설문·모집글에서 온 정상 경로)
        for (String tier : tierOrder()) {
            if (tier.equalsIgnoreCase(trimmed)) return tier;
        }
        // 2) 별칭 (라이엇 영문 enum, 옛 표기, 세분화된 상위 티어)
        String alias = tierAliases().get(trimmed.toUpperCase(Locale.ROOT));
        if (alias != null) return alias;

        // 3) "다이아몬드 II" 처럼 세부 등급이 붙어 온 경우 — 티어 이름만 떼어낸다.
        //    contains 로 훑되 긴 이름부터 봐야 "그랜드마스터"가 "마스터"로 잡히지 않는다.
        return tierOrder().stream()
                .filter(tier -> trimmed.toUpperCase(Locale.ROOT).contains(tier.toUpperCase(Locale.ROOT)))
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
    }

    @Override
    public boolean tierInRange(String myTier, String wantedTier) {
        if (isAnyOrBlank(wantedTier)) return true; // 모집글이 티어 무관

        // indexOf(null) 은 List.of(...) 같은 불변 리스트에서 NPE 를 던진다.
        // normalizeTier 는 모르는 값에 null 을 돌려주므로 여기서 먼저 막는다.
        int mine = rankOf(myTier);
        if (mine < 0) return true; // 내 티어를 모르면(언랭·미연동) 걸러내지 않는다

        int wanted = rankOf(wantedTier);
        if (wanted < 0) return true; // 모집글의 티어 값을 서열표에서 못 찾으면(오타 등) 걸러내지 않는다

        return Math.abs(mine - wanted) <= TIER_TOLERANCE;
    }

    /** 서열표에서의 위치. 모르는 값이면 -1. */
    private int rankOf(String rawTier) {
        String normalized = normalizeTier(rawTier);
        return normalized == null ? -1 : tierOrder().indexOf(normalized);
    }

    private boolean isAnyOrBlank(String s) {
        return s == null || s.isBlank() || s.contains("상관없음");
    }

    private List<String> splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
