package gg.duo.match.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * BusinessException/IllegalArgumentException·IllegalStateException/NoSuchElementException/
 * SecurityException 은 common.exception.GlobalExceptionHandler 가 이미 처리한다
 * (user/post/chat/riot 과 동일). 여기서는 match 만 겪는 예외 하나만 추가로 잡는다.
 *
 * user/post 서비스 호출이 실패했을 때(4xx/5xx, 타임아웃 포함) — match-service 자체
 * 버그가 아니라 "다른 서비스에 못 물어봤다"는 뜻이므로 502로 구분해서 프론트가
 * "잠시 후 다시 시도해주세요" 같은 안내를 붙일 수 있게 한다. WebClient(webflux)를
 * 쓰는 match/riot 에서만 나는 예외라 common 에는 두지 않았다 — RestClient 를 쓰는
 * post/chat 은 RestClientException 계열이라 애초에 종류가 다르다.
 */
@RestControllerAdvice
public class UpstreamCallExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, String>> upstream(WebClientResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "다른 서비스에서 정보를 가져오지 못했습니다."));
    }
}
