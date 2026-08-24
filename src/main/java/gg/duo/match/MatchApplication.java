package gg.duo.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * match 서비스 — Team Fit 추천.
 *
 * 소유 테이블: match_requests, match_results, recommendation_events, user_preferences
 *
 * gg.duo.common 을 함께 스캔해야 JwtAuthFilter/JwtTokenProvider/SecurityBaseConfig/
 * GlobalExceptionHandler/RabbitEventConfig 같은 공통 빈이 뜬다 — user/post/chat/riot
 * 과 동일한 패턴이다.
 */
@SpringBootApplication(scanBasePackages = {"gg.duo.match", "gg.duo.common"})
public class MatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchApplication.class, args);
    }
}
