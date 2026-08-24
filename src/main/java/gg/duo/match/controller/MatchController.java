package gg.duo.match.controller;

import gg.duo.match.dto.MatchSearchRequest;
import gg.duo.match.dto.MatchSearchResponse;
import gg.duo.match.dto.RecommendationEventRequest;
import gg.duo.match.service.MatchSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchSearchService matchSearchService;

    private Long userId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    /**
     * 조건에 맞는 파티 추천 검색.
     * user/post 서비스 호출을 위해 원본 요청의 Authorization 헤더를 그대로 넘긴다.
     */
    @PostMapping("/search")
    public MatchSearchResponse search(Authentication auth,
                                      @RequestHeader("Authorization") String authorizationHeader,
                                      @RequestBody MatchSearchRequest req) {
        return matchSearchService.search(userId(auth), authorizationHeader, req);
    }

    /** 추천 결과 클릭/지원 등 사용자 반응 기록 (노출·클릭·지원 로그) */
    @PostMapping("/results/{resultId}/events")
    public void recordEvent(@PathVariable Long resultId,
                            Authentication auth,
                            @RequestBody RecommendationEventRequest req) {
        matchSearchService.recordEvent(resultId, req);
    }
}
