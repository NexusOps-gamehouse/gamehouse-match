package gg.duo.match.strategy;

import java.util.List;

/**
 * 게임마다 다른 Hard Filter 어휘(포지션/역할 이름, 티어 체계)를 다루는 확장 지점.
 *
 * Team Fit v2부터 포지션/티어는 점수(Soft Score)가 아니라 Hard Filter다 — 회의록
 * "Team Fit 점수 산정 방식" 표에서 포지션/역할·티어 범위가 배점에서 빠지고 전부
 * Hard Filter 쪽으로 이동했다. 그래서 이 인터페이스는 더 이상 점수를 계산하지 않고
 * "통과/제외"만 판단한다 — 실제 점수(TeamFitCalculator)는 게임과 무관한 성향 축만
 * 다룬다.
 *
 * 새 게임을 추가할 때는 구현체 하나만 추가하면 되고, HardFilterService는 손대지
 * 않는다.
 */
public interface GameMatchingStrategy {

    /** Post.game / User.game 문자열과 매칭되는 코드. 대소문자 무시하고 비교한다. */
    String gameCode();

    /**
     * 내가 채우려는 포지션/역할 목록(searchPositions) 중 하나라도 모집글이 찾는
     * 포지션(postWantedPositionsCsv)에 포함되면 통과. 둘 중 하나라도 "상관없음"이거나
     * 비어있으면 무조건 통과(제한 없음으로 취급)한다.
     */
    boolean positionMatches(List<String> searchPositions, String postWantedPositionsCsv);

    /**
     * 내 티어(myTier)가 모집글이 원하는 티어 범위[tierMin, tierMax] 안에 있으면 통과.
     * 범위 값이 없거나("상관없음") 내 티어를 알 수 없으면(자기신고·라이엇 연동 둘 다 없음)
     * 걸러내지 않고 통과시킨다 — 정보 부족을 이유로 후보를 지나치게 줄이지 않기 위해서다.
     */
    boolean tierInRange(String myTier, String tierMin, String tierMax);
}
