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
        double contribution,   // score * weight / 100
        /**
         * 실제로 비교해서 나온 점수인가, 데이터가 없어 중립값으로 채운 자리인가.
         *
         * 둘을 구분하지 않으면 "설문/프로필을 아무도 안 채운 축"이 중립값 50점으로
         * 내려가면서 AI 설명의 "60점 미만이면 주의 문구" 규칙에 걸린다. 그러면
         * 재본 적도 없는 항목을 두고 "플레이 시간대는 차이가 있어요"라고 말하게 된다 —
         * 모른다와 안 맞는다는 다른 말이다.
         *
         * ExplanationService 는 known=false 인 축을 설명 대상에서 빼고, 프론트는
         * 세부 점수 UI 에서 흐리게 표시한다. 점수 자체는 그대로 총점에 들어간다
         * (축마다 배점이 고정이라 빼면 후보 간 총점을 비교할 수 없다).
         */
        boolean known
) {
}
