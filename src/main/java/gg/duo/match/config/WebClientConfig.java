package gg.duo.match.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * user/post는 더 이상 하나의 모놀리스가 아니라 각자 다른 파드/포트로 뜨는 서비스다
 * (user :8081, post :8082). 그래서 base-url 이 다른 WebClient 를 두 개 둔다 —
 * user/post/chat/riot 의 application.yml 에 이미 있는 services.user.base-url /
 * services.post.base-url 을 그대로 재사용한다(로컬 기본값도 동일).
 *
 * WebClient(webflux)를 쓰는 이유는 riot/RiotConfig 와 같다: user/post 응답을
 * JsonNode로 받아 match 내부 DTO로 매핑하는 client(UserClient/PostClient)가
 * 이미 그렇게 짜여 있고, 두 서비스가 죽어 있어도(WebClientResponseException)
 * 매칭 자체가 죽지 않도록 방어적으로 파싱한다.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient userServiceWebClient(@Value("${services.user.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public WebClient postServiceWebClient(@Value("${services.post.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
