package gg.duo.match.domain.result;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 검색 결과 한 건의 스냅샷. "그때 몇 등에 몇 점으로, 어떤 축 때문에 왜 이 글을
 * 보여줬는지"를 축 단위까지 그대로 남겨야 알고리즘을 바꾼 뒤 전/후 비교가 가능하다.
 * score/rank/algoVersion/axesJson은 나중에 소급 적용이 안 되는 값이라 지금부터
 * 반드시 채워 넣는다.
 */
@Entity
@Table(name = "match_results")
@Getter
@Setter
@NoArgsConstructor
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchRequestId;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private String algoVersion;

    @Column(nullable = false)
    private int partySize;

    /** FitAxis 리스트(JSON). 응답에만 있고 DB엔 없던 축별 세부 점수를 스냅샷으로 보강한다. */
    @Column(columnDefinition = "TEXT")
    private String axesJson;

    /** 1위 결과에 한해 채워지는 AI(또는 Fallback) 설명 스냅샷(JSON, ExplanationDto). */
    @Column(columnDefinition = "TEXT")
    private String explanationJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
