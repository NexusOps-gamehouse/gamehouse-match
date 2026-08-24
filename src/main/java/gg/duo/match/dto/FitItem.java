package gg.duo.match.dto;

import java.util.List;

/**
 * 검색 결과 한 건. score/rank/axes는 응답에도 싣고 MatchResult 스냅샷에도 그대로 저장해서
 * "그때 왜 이 순서로 보여줬는지"를 축 단위까지 그대로 재현할 수 있게 한다.
 */
public record FitItem(
        Long postId,
        Long resultId,          // MatchResult 저장 후 채워짐 — 클릭 이벤트 기록에 쓴다
        String title,
        String authorNickname,
        int rank,                // 1부터
        double score,             // 0~100, Team Fit 종합 점수
        List<FitAxis> axes,       // 기여도(contribution) 내림차순 정렬됨
        int partySize,            // 작성자 포함, 계산에 쓰인 파티 인원 수
        boolean micRequired,
        String positions,
        String playTime,
        long currentMembers,
        int targetMembers
) {
}
