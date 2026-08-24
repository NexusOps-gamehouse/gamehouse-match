package gg.duo.match.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 심화 프로젝트 필수 요건 "AI 기능 1개 이상 적용"을 위한 설정.
 * ExplanationService가 매칭 1위 결과에 대한 한 줄 설명을 생성할 때 쓴다.
 *
 * llm.api.key 가 dummy_key(기본값)면 ExplanationService가 실제 호출 대신
 * 규칙 기반 문구로 대체하므로, 키가 없어도 서비스 자체는 정상 동작한다.
 */
@Configuration
public class LlmConfig {

    @Bean
    public WebClient llmWebClient(@Value("${llm.api.url}") String apiUrl) {
        // apiUrl이 "https://api.openai.com/v1/chat/completions" 같은 완전한 엔드포인트라
        // baseUrl로 두지 않고 ExplanationService에서 그대로 post(URI)로 사용한다.
        return WebClient.builder().build();
    }
}
