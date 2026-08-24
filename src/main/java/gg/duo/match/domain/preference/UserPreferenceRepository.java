package gg.duo.match.domain.preference;

import org.springframework.data.jpa.repository.JpaRepository;

// 아직 어디서도 안 쓴다. 개인화 기능 시작할 때 채운다.
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
}
