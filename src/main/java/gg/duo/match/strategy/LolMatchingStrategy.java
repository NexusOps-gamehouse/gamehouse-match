package gg.duo.match.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LoL 어휘.
 *
 * [서열표의 출처] post 서비스 GameOptions.TIERS(LOL) 와 같은 목록이다. 모집글은
 * 그 목록으로 검증되어 저장되므로 DB 에 들어올 수 있는 티어 값이 이 8개로 고정된다.
 * 예전에는 여기에 "마스터 / 그랜드마스터 / 챌린저"를 따로 뒀는데, 그렇게 저장된
 * 모집글은 존재할 수 없어 서열표만 어긋난 상태였다. 지금은 상위 세 티어를
 * "마스터 이상" 하나로 접고, 세분화된 표기는 별칭으로 흡수한다.
 *
 * [허용 폭 ±1] post 는 티어를 범위가 아니라 값 하나(wantedTier)로만 받고, 허용 폭은
 * AbstractGameMatchingStrategy.TIER_TOLERANCE(±1단계)가 정한다. 상위 세 티어를
 * 한 칸으로 접은 덕분에 "마스터를 찾는 글에서 챌린저가 걸러지던" 문제도 같이
 * 사라졌다 — 셋이 같은 칸이다.
 */
@Component
public class LolMatchingStrategy extends AbstractGameMatchingStrategy {

    /** GameOptions.TIERS(GameCode.LOL) 와 동일해야 한다. */
    private static final List<String> TIER_ORDER = List.of(
            "아이언", "브론즈", "실버", "골드", "플래티넘", "에메랄드", "다이아몬드", "마스터 이상"
    );

    /** GameOptions.ROLES(GameCode.LOL) 와 동일해야 한다. */
    private static final List<String> POSITIONS = List.of("탑", "정글", "미드", "원딜", "서폿");

    /**
     * User.riotTier 는 라이엇 영문 enum("DIAMOND")으로 저장된다. 정규화 없이
     * 서열표를 뒤지면 못 찾고, tierInRange 가 "내 티어를 모른다"며 전부 통과시켜
     * **라이엇을 연동한 사용자일수록 티어 필터가 안 걸리는** 상태였다.
     * 프론트 api/riot.js 의 TIER_TABLE 과 같은 대응이다.
     */
    private static final Map<String, String> TIER_ALIASES = Map.ofEntries(
            Map.entry("IRON", "아이언"),
            Map.entry("BRONZE", "브론즈"),
            Map.entry("SILVER", "실버"),
            Map.entry("GOLD", "골드"),
            Map.entry("PLATINUM", "플래티넘"),
            Map.entry("EMERALD", "에메랄드"),
            Map.entry("DIAMOND", "다이아몬드"),
            Map.entry("MASTER", "마스터 이상"),
            Map.entry("GRANDMASTER", "마스터 이상"),
            Map.entry("CHALLENGER", "마스터 이상"),
            Map.entry("마스터", "마스터 이상"),
            Map.entry("그랜드마스터", "마스터 이상"),
            Map.entry("챌린저", "마스터 이상")
    );

    @Override
    public String gameCode() {
        return "LOL";
    }

    @Override
    protected List<String> tierOrder() {
        return TIER_ORDER;
    }

    @Override
    protected Map<String, String> tierAliases() {
        return TIER_ALIASES;
    }

    @Override
    protected List<String> positions() {
        return POSITIONS;
    }
}
