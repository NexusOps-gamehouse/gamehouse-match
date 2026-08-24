package gg.duo.match.dto;

/**
 * Team Fit 세부 축 하나. score/weight/contribution은 AI 설명(ExplanationService)
 * 입력 스키마, MatchResult 스냅샷, 프론트 "세부 점수 UI"가 전부 공유하는 구조다.
 * contribution = score * weight / 100.
 */
public record FitAxis(
        String axis,          // 예: "승리 지향성"
        double score,          // 0~100, 이 축에서의 궁합 점수
        double weight,         // 0~100, 배점
        double contribution    // score * weight / 100
) {
}
