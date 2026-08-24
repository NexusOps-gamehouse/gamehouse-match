package gg.duo.match.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 발로란트는 LOL과 티어 체계가 달라(초월자/불멸/레디언트) 별도 서열표를 쓴다.
 * "이니시에이터/컨트롤러/센티널/듀얼리스트" 같은 역할 명칭은 positions 필드에
 * 그대로 CSV로 담기므로 상위 클래스의 positionMatches를 그대로 재사용한다.
 */
@Component
public class ValorantMatchingStrategy extends AbstractGameMatchingStrategy {

    private static final List<String> TIER_ORDER = List.of(
            "아이언", "브론즈", "실버", "골드", "플래티넘", "다이아몬드", "초월자", "불멸", "레디언트"
    );

    @Override
    public String gameCode() {
        return "VALORANT";
    }

    @Override
    protected List<String> tierOrder() {
        return TIER_ORDER;
    }
}
