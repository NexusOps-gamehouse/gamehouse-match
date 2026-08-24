package gg.duo.match.domain.request;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 검색 1회 = MatchRequest 1건. "누가 언제 무슨 조건으로 검색했는지"를 남긴다.
 * user 서비스로 FK를 걸지 않는다 — 크로스 서비스 FK는 두지 않기로 했으므로
 * requesterId는 그냥 Long이다.
 *
 * positionsRequested/micRequired/targetMembersOptions는 /match/new에서 사용자가
 * 직접 고른 검색 조건 스냅샷이다 — "그때 무슨 조건으로 찾았는지" 재현하거나,
 * 나중에 인기 검색 조건을 분석(STORY 12 등)할 때 쓸 수 있다.
 */
@Entity
@Table(name = "match_requests")
@Getter
@Setter
@NoArgsConstructor
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requesterId;

    @Column(nullable = false)
    private String game;

    private String gameMode;

    private String positionsRequested; // 콤마 구분

    private Boolean micRequired;       // (구버전 호환용) null=상관없음

    private String micLevel;           // REQUIRED | PREFERRED | ANY

    private String tier;               // 이 검색에서 오버라이드한 티어(없으면 프로필 값을 썼다는 뜻)

    private String playStyle;          // 참고용 — 아직 필터/점수에는 반영하지 않는다

    private String targetMembersOptions; // 콤마 구분 숫자, 예: "3,4,5"

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
