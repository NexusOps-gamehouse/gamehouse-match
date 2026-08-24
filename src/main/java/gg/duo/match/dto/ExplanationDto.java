package gg.duo.match.dto;

import java.util.List;

/**
 * AI(또는 실패 시 규칙 기반 Fallback)가 만든 추천 이유. LLM은 이 구조 그대로
 * JSON을 출력하도록 프롬프트로 강제한다 — 숫자를 직접 노출하지 않고, 반드시
 * 근거가 된 축 이름만으로 서술한다.
 */
public record ExplanationDto(
        String headline,
        List<String> reasons,
        String caution   // 60점 미만 축이 없으면 null
) {
}
