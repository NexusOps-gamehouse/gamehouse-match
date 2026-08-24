package gg.duo.match.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.duo.match.client.PostClient;
import gg.duo.match.client.UserClient;
import gg.duo.match.domain.event.RecommendationEvent;
import gg.duo.match.domain.event.RecommendationEventRepository;
import gg.duo.match.domain.request.MatchRequest;
import gg.duo.match.domain.request.MatchRequestRepository;
import gg.duo.match.domain.result.MatchResult;
import gg.duo.match.domain.result.MatchResultRepository;
import gg.duo.match.dto.ExplanationDto;
import gg.duo.match.dto.FitItem;
import gg.duo.match.dto.MatchSearchRequest;
import gg.duo.match.dto.MatchSearchResponse;
import gg.duo.match.dto.PostSummaryDto;
import gg.duo.match.dto.RecommendationEventRequest;
import gg.duo.match.dto.UserSummaryDto;
import gg.duo.match.event.publisher.MatchFoundPublisher;
import gg.duo.match.service.TeamFitCalculator.TeamFitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 전체 검색 흐름을 조립하는 진입점.
 * user/post 조회 → 하드 필터(+완화 사다리) → Team Fit 점수 계산 → 정렬 →
 * 결과 스냅샷 저장 → 1위 AI 설명 생성, 순서로 진행한다.
 *
 * 회의록 "핵심 사용자 시나리오"의 "실시간으로 계속 갱신하지 않는다" 원칙에 따라,
 * 이 메서드가 반환하는 결과는 호출 시점의 스냅샷이다 — calculatedAt 이후 다른
 * 사용자가 참가/마감을 해도 이 응답은 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchSearchService {

    private static final String ALGO_VERSION = "match-v2-teamfit";

    private final UserClient userClient;
    private final PostClient postClient;
    private final HardFilterService hardFilterService;
    private final TeamFitCalculator teamFitCalculator;
    private final ExplanationService explanationService;
    private final MatchFoundPublisher matchFoundPublisher;
    private final ObjectMapper objectMapper;

    private final MatchRequestRepository matchRequestRepository;
    private final MatchResultRepository matchResultRepository;
    private final RecommendationEventRepository recommendationEventRepository;

    @Transactional
    public MatchSearchResponse search(Long meId, String authorizationHeader, MatchSearchRequest request) {
        if (request.game() == null || request.game().isBlank()) {
            throw new IllegalArgumentException("game은 필수입니다.");
        }

        UserSummaryDto me = userClient.getUser(meId, authorizationHeader);
        List<PostSummaryDto> candidates = postClient.listRecruiting(request.game(), request.gameMode(), authorizationHeader);
        HardFilterService.Outcome filtered = hardFilterService.filter(me, candidates, request);

        record Scored(PostSummaryDto post, TeamFitResult fit) {
        }
        List<Scored> scored = filtered.posts().stream()
                .map(post -> new Scored(post, teamFitCalculator.calculate(me, post)))
                .sorted(Comparator.comparingDouble((Scored s) -> s.fit().total()).reversed())
                .limit(request.limitOrDefault())
                .toList();

        Instant calculatedAt = Instant.now();

        MatchRequest savedRequest = new MatchRequest();
        savedRequest.setRequesterId(meId);
        savedRequest.setGame(request.game());
        savedRequest.setGameMode(request.gameMode());
        savedRequest.setPositionsRequested(joinOrNull(request.positions()));
        savedRequest.setMicRequired(request.micRequired());
        savedRequest.setMicLevel(request.micLevel());
        savedRequest.setTier(request.tier());
        savedRequest.setPlayStyle(request.playStyle());
        savedRequest.setTargetMembersOptions(joinIntsOrNull(request.targetMembersOptions()));
        matchRequestRepository.save(savedRequest);

        List<FitItem> items = new ArrayList<>();
        int rank = 1;
        for (Scored s : scored) {
            MatchResult result = new MatchResult();
            result.setMatchRequestId(savedRequest.getId());
            result.setPostId(s.post().id());
            result.setRank(rank);
            result.setScore(s.fit().total());
            result.setAlgoVersion(ALGO_VERSION);
            result.setPartySize(s.fit().partySize());
            result.setAxesJson(toJson(s.fit().axes()));
            matchResultRepository.save(result);

            items.add(new FitItem(
                    s.post().id(),
                    result.getId(),
                    s.post().title(),
                    s.post().authorNickname(),
                    rank,
                    s.fit().total(),
                    s.fit().axes(),
                    s.fit().partySize(),
                    s.post().micRequired(),
                    s.post().positions(),
                    s.post().playTime(),
                    s.post().currentMembers(),
                    s.post().targetMembers()
            ));
            rank++;
        }

        ExplanationDto topExplanation;
        if (scored.isEmpty()) {
            topExplanation = new ExplanationDto(
                    "지금 조건에 맞는 모집글이 없어요.", List.of(), "필터를 조금 넓혀보세요.");
        } else {
            topExplanation = explanationService.explain(scored.get(0).fit());
            // 1위 MatchResult에 설명도 스냅샷으로 남긴다 — "그때 왜 이렇게 설명했는지"를
            // 알고리즘/프롬프트가 바뀐 뒤에도 그대로 재현할 수 있도록.
            Long topResultId = items.get(0).resultId();
            String explanationJson = toJson(topExplanation);
            matchResultRepository.findById(topResultId).ifPresent(r -> {
                r.setExplanationJson(explanationJson);
                matchResultRepository.save(r);
            });
        }

        return new MatchSearchResponse(items, topExplanation, ALGO_VERSION, filtered.relaxed(), calculatedAt);
    }

    /**
     * 노출/클릭/지원 로그. score·rank·algoVersion은 MatchResult에서 그대로 복사해
     * 결과가 나중에 바뀌거나 지워져도 이벤트 시점 값이 남도록 한다.
     */
    @Transactional
    public void recordEvent(Long resultId, RecommendationEventRequest request) {
        MatchResult result = matchResultRepository.findById(resultId)
                .orElseThrow(() -> new NoSuchElementException("추천 결과를 찾을 수 없습니다."));

        RecommendationEvent.EventType type;
        try {
            type = RecommendationEvent.EventType.valueOf(request.eventType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("eventType은 IMPRESSION | CLICK | APPLY 중 하나여야 합니다.");
        }

        RecommendationEvent event = new RecommendationEvent();
        event.setMatchResultId(result.getId());
        event.setPostId(result.getPostId());
        event.setEventType(type);
        event.setRank(result.getRank());
        event.setScore(result.getScore());
        event.setAlgoVersion(result.getAlgoVersion());
        recommendationEventRepository.save(event);

        if (type == RecommendationEvent.EventType.APPLY) {
            MatchRequest matchRequest = matchRequestRepository.findById(result.getMatchRequestId()).orElse(null);
            Long requesterId = matchRequest == null ? null : matchRequest.getRequesterId();
            String gameCode = matchRequest == null ? null : matchRequest.getGame();
            matchFoundPublisher.publish(result.getId(), result.getPostId(), requesterId, gameCode);
        }
    }

    private String joinOrNull(List<String> values) {
        return (values == null || values.isEmpty()) ? null : String.join(",", values);
    }

    private String joinIntsOrNull(List<Integer> values) {
        if (values == null || values.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 직렬화 실패 — 스냅샷 저장을 건너뜁니다.", e);
            return null;
        }
    }
}
