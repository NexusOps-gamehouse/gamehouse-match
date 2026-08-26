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
 * [어휘의 기준점] 이 구현체들이 쓰는 포지션/티어 문자열은 post 서비스의
 * GameOptions(ROLES/TIERS)와 **정확히 같아야 한다**. GameOptions 는 모집글 저장을
 * 검증하는 쪽이라 DB 에 실제로 들어가는 값이 그 목록으로 고정되기 때문이다.
 * 여기 목록이 한 글자라도 다르면("초월" vs "초월자") 필터가 에러 없이 그냥
 * 꺼지거나 결과가 0건이 된다 — 화면만 봐서는 원인을 찾을 수 없는 종류의 버그다.
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
     * 이 게임의 포지션/역할 어휘에 속하는 값인가.
     *
     * 프로필의 position 은 게임을 구분하지 않고 한 칸에 저장된다("정글"). 그 값을
     * 다른 게임 검색의 기본 포지션으로 쓰면 발로란트 글은 하나도 안 걸린다.
     * HardFilterService 가 폴백 전에 이걸로 확인한다.
     */
    boolean knowsPosition(String position);

    /**
     * 어떤 표기로 들어온 티어든 이 게임의 서열표 값으로 바꾼다. 못 알아보면 null.
     *
     * 티어 문자열이 들어오는 경로가 세 갈래라 필요하다.
     *   - 설문에서 직접 고른 한글 값        "다이아몬드"
     *   - 라이엇 연동으로 받은 영문 enum     "DIAMOND"   (User.riotTier)
     *   - 모집글이 저장한 GameOptions 값     "마스터 이상"
     * 예전에는 정규화 없이 서열표를 뒤져서, 라이엇을 연동한 사용자는 영문 값이
     * 서열표에 없다는 이유로 티어 필터가 통째로 통과 처리됐다.
     */
    String normalizeTier(String rawTier);

    /**
     * 내 티어(myTier)가 모집글이 찾는 티어(wantedTier) 기준 허용 폭 안에 있으면 통과.
     * 두 값 모두 normalizeTier 를 거친 뒤 비교한다.
     *
     * post 서비스는 티어를 범위(min~max)가 아니라 "이 티어인 사람을 찾는다"는
     * 값 하나로만 받는다(PostGameRequirement.tier 참고 — 하한·상한을 각각 고르게
     * 하면 대부분 아무거나 넣는다는 이유로 범위 입력 자체를 안 받기로 했다).
     * 그래서 이 값 기준 허용 폭은 매칭 쪽(구현체의 tierOrder + 허용 폭 상수)이 정한다.
     *
     * wantedTier가 없거나("상관없음") 내 티어를 알 수 없으면(언랭·미연동) 걸러내지
     * 않고 통과시킨다 — 정보 부족을 이유로 후보를 지나치게 줄이지 않기 위해서다.
     */
    boolean tierInRange(String myTier, String wantedTier);
}
