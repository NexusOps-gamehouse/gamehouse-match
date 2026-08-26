package gg.duo.match.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 발로란트 어휘. LOL과 티어 체계도 역할 명칭도 달라 별도 목록을 쓴다.
 *
 * [고쳐진 두 가지]
 *   - 티어: 여기가 "초월자"였는데 모집글에 실제로 저장되는 값은 GameOptions 의
 *     "초월"이다. 서열표에서 못 찾으니 그 티어를 건 글은 전부 "티어 무관"으로
 *     빠져나갔다. GameOptions.TIERS(VALORANT) 에 맞추고 "초월자"는 별칭으로 받는다.
 *   - 역할: 프론트 스마트 매칭 폼만 "듀얼리스트/이니시에이터/컨트롤러/센티널"을
 *     쓰고 있었는데, 모집글은 GameOptions 검증을 통과한 "타격대/척후대/전략가/
 *     감시자"로만 저장된다. 두 어휘가 겹치는 값이 하나도 없어 발로란트에서 역할을
 *     고르면 결과가 항상 0건이었다. 기준은 저장되는 쪽(GameOptions)이다.
 */
@Component
public class ValorantMatchingStrategy extends AbstractGameMatchingStrategy {

    /** GameOptions.TIERS(GameCode.VALORANT) 와 동일해야 한다. */
    private static final List<String> TIER_ORDER = List.of(
            "아이언", "브론즈", "실버", "골드", "플래티넘", "다이아몬드", "초월", "불멸", "레디언트"
    );

    /** GameOptions.ROLES(GameCode.VALORANT) 와 동일해야 한다. */
    private static final List<String> POSITIONS = List.of("타격대", "척후대", "전략가", "감시자");

    /**
     * 발로란트는 아직 라이엇 연동(User.riotTier)이 없지만, 옛 표기와 영문 표기를
     * 미리 흡수해 둔다. "초월자"는 프론트 옛 목록에서 넘어올 수 있는 값이고,
     * 영문 enum 은 연동이 붙는 시점에 그대로 쓰인다.
     */
    private static final Map<String, String> TIER_ALIASES = Map.ofEntries(
            Map.entry("초월자", "초월"),
            Map.entry("IRON", "아이언"),
            Map.entry("BRONZE", "브론즈"),
            Map.entry("SILVER", "실버"),
            Map.entry("GOLD", "골드"),
            Map.entry("PLATINUM", "플래티넘"),
            Map.entry("DIAMOND", "다이아몬드"),
            Map.entry("ASCENDANT", "초월"),
            Map.entry("IMMORTAL", "불멸"),
            Map.entry("RADIANT", "레디언트")
    );

    @Override
    public String gameCode() {
        return "VALORANT";
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
