package gg.duo.match.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationEventRepository extends JpaRepository<RecommendationEvent, Long> {
}
