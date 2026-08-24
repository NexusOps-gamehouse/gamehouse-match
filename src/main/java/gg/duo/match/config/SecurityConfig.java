package gg.duo.match.config;

import gg.duo.common.security.SecurityBaseConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * common.security.SecurityBaseConfig 을 그대로 물려받는다(riot/post/chat/user 와 같은 패턴).
 * JWT 검증(JwtAuthFilter)·CORS·actuator 보안·401 응답 형태는 전부 common 이 담당한다.
 *
 * match 의 API는 전부 로그인이 필요한 /api/match/** 뿐이다. permitAll 로 열어줄
 * 공개 엔드포인트가 없으므로 configurePublicEndpoints 를 오버라이드하지 않는다
 * (기본 구현이 아무것도 열지 않는다).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends SecurityBaseConfig {
}
