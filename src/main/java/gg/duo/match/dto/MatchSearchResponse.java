package gg.duo.match.dto;

import java.time.Instant;
import java.util.List;

public record MatchSearchResponse(
        List<FitItem> results,
        ExplanationDto topExplanation, // 1위 결과에 대한 AI 생성 설명. LLM 미설정/실패 시 규칙 기반 문구
        String algoVersion,
        boolean relaxed,      // 완화 사다리가 적용됐는지 (조건을 정확히 만족하는 글이 부족했다는 뜻)
        Instant calculatedAt  // 이 스냅샷이 계산된 시각 — "다시 매칭" 전까지 화면에 고정 표시한다
) {
}
