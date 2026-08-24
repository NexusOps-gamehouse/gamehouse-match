package gg.duo.match.domain.preference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 개인화(나중) 용 자리만 잡아 둔 엔티티. 지금은 어디서도 쓰지 않는다.
 *
 * 나중에 "이 사람은 항상 포지션 점수보다 시간대 점수를 더 중요하게 여기더라" 같은
 * 걸 학습해서 TeamFitCalculator의 가중치를 사용자별로 조정할 때 쓸 자리.
 * key/value로 단순하게 열어둔 이유는, 지금 시점에 어떤 선호를 저장할지 확정되지
 * 않았기 때문 — 구체적인 컬럼을 미리 잡으면 나중에 마이그레이션이 더 커진다.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String preferenceKey;

    @Column(nullable = false)
    private String preferenceValue;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
