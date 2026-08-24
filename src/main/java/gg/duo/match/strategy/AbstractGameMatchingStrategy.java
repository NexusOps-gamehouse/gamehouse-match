package gg.duo.match.strategy;

import java.util.Arrays;
import java.util.List;

/**
 * LOL/발로란트 두 구현체가 공유하는 로직. 게임마다 다른 건 티어 서열표 하나뿐이라
 * tierOrder()만 하위 클래스가 채우면 된다.
 */
abstract class AbstractGameMatchingStrategy implements GameMatchingStrategy {

    /** 하위 티어 → 상위 티어 순서. 게임마다 명칭이 다르다(LOL 마스터+ vs 발로란트 레디언트). */
    protected abstract List<String> tierOrder();

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
    public boolean tierInRange(String myTier, String tierMin, String tierMax) {
        List<String> order = tierOrder();
        int mine = indexOf(order, myTier);
        if (mine < 0) return true; // 내 티어를 모르면 걸러내지 않는다

        int minIdx = indexOf(order, tierMin);
        int maxIdx = indexOf(order, tierMax);
        boolean minOk = isAnyOrBlank(tierMin) || minIdx < 0 || mine >= minIdx;
        boolean maxOk = isAnyOrBlank(tierMax) || maxIdx < 0 || mine <= maxIdx;
        return minOk && maxOk;
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

    private int indexOf(List<String> order, String tier) {
        if (tier == null || tier.isBlank()) return -1;
        String normalized = tier.trim();
        for (int i = 0; i < order.size(); i++) {
            if (normalized.contains(order.get(i))) return i;
        }
        return -1;
    }
}
