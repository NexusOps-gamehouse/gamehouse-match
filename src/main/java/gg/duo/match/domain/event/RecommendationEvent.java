package gg.duo.match.domain.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 노출(IMPRESSION)/클릭(CLICK)/지원(APPLY) 로그. "94점짜리를 눌렀나 60점짜리를
 * 눌렀나"를 나중에 알 수 있어야 하므로 score·rank·algoVersion을 결과와 별개로
 * 이벤트에도 그대로 복사해 둔다 — MatchResult가 나중에 지워지거나 바뀌어도
 * 이벤트 시점의 값은 보존된다.
 */
@Entity
@Table(name = "recommendation_events")
@Getter
@Setter
@NoArgsConstructor
public class RecommendationEvent {

    public enum EventType { IMPRESSION, CLICK, APPLY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchResultId;

    @Column(nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private String algoVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
