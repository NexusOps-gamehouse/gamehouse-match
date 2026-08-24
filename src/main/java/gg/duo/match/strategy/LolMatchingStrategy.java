package gg.duo.match.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 회의록 기준 LoL 티어 표기는 "아이언 ~ 마스터 이상"이다. 마스터/그랜드마스터/챌린저를
 * 내부적으로는 세 단계로 그대로 두되, "마스터 이상"을 상한으로 선택했을 때는
 * 프론트/기획에서 tierMax를 null(=상한 없음)로 보내는 것을 전제로 한다 — 그래야
 * 그랜드마스터·챌린저 지원자가 "마스터"보다 높다는 이유로 부당하게 걸러지지 않는다.
 */
@Component
public class LolMatchingStrategy extends AbstractGameMatchingStrategy {

    private static final List<String> TIER_ORDER = List.of(
            "아이언", "브론즈", "실버", "골드", "플래티넘", "에메랄드", "다이아몬드", "마스터", "그랜드마스터", "챌린저"
    );

    @Override
    public String gameCode() {
        return "LOL";
    }

    @Override
    protected List<String> tierOrder() {
        return TIER_ORDER;
    }
}
