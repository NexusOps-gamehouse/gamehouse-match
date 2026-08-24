package gg.duo.match.domain.result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    List<MatchResult> findByMatchRequestId(Long matchRequestId);
}
