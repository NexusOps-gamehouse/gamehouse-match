package gg.duo.match.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.duo.match.client.PostClient;
import gg.duo.match.client.PostPartyClient;
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
import gg.duo.match.dto.PartyBrief;
import gg.duo.match.dto.PartyMemberDto;
import gg.duo.match.dto.PersonalityProfile;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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

    private static final String ALGO_VERSION = "match-v3-teamfit";

    private final UserClient userClient;
    private final PostClient postClient;
    private final PostPartyClient postPartyClient;
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

        // request.playTime 은 "이번 검색에서는 이 시간대로 취급해줘"라는 검색 시점
        // 오버라이드다(HardFilterService 가 request.tier 를 다루는 방식과 같다).
        // 비어 있으면 프로필의 playTimes 를 그대로 쓴다.
        UserSummaryDto me = userClient.getUser(meId, authorizationHeader)
                .withPlayTimesOverride(request.playTime());
        List<PostSummaryDto> candidates = postClient.listRecruiting(request.game(), request.gameMode(), authorizationHeader);
        HardFilterService.Outcome filtered = hardFilterService.filter(me, candidates, request);

        // 하드 필터를 통과한 후보만 성향/파티원을 채운다 — 걸러질 후보까지 내부 API를
        // 부르면 낭비다. 여기서부터 아래 흐름 전체가 이번 정리(성향 내부 API 분리,
        // CONFIRMED 파티원 반영)로 새로 생긴 조립 단계다.
        Enriched enriched = enrich(meId, me, filtered.posts());

        record Scored(PostSummaryDto post, TeamFitResult fit) {
        }
        List<Scored> scored = enriched.posts().stream()
                .map(post -> new Scored(post, teamFitCalculator.calculate(enriched.me(), post)))
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
        // playTime 은 요청 DTO 에만 있고 저장되지 않아, "왜 이런 결과가 나왔는지"를
        // 나중에 재현할 수 없는 유일한 검색 조건이었다. 이제 점수(플레이 시간대 축)에
        // 실제로 쓰이는 값이라 더더욱 남겨야 한다.
        savedRequest.setPlayTime(request.playTime());
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
                    toPartyBriefs(s.post()),
                    s.fit().surveyedCount(),
                    s.post().micRequired(),
                    s.post().roles(),
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

    private record Enriched(UserSummaryDto me, List<PostSummaryDto> posts) {
    }

    /**
     * 하드 필터를 통과한 후보에 성향 점수(personality)와 확정 파티원(members)을 채운다.
     *
     * 공개 API(GET /api/users/{id}, GET /api/posts)에는 이 값들이 없다 — 성향 점수는
     * "남의 프로필 조회"로 노출하면 안 되는 값이라 내부 전용 엔드포인트로 분리했고,
     * "이 글에 누가 확정으로 들어와 있는가"도 지금까지 방장만 볼 수 있던 정보라
     * 같이 내부 전용으로 뒀다(PostSummaryDto/UserSummaryDto 주석 참고).
     *
     * 필요한 id(나 + 작성자들 + 파티원들)를 한 번에 모아 성향 조회를 1회로 묶는다 —
     * 후보마다 부르면 후보 수만큼 내부 API 왕복이 생긴다.
     */
    private Enriched enrich(Long meId, UserSummaryDto me, List<PostSummaryDto> posts) {
        Map<Long, List<Long>> partyByPost =
                postPartyClient.fetchPartyMembers(posts.stream().map(PostSummaryDto::id).toList());

        Set<Long> personalityTargets = new HashSet<>();
        Set<Long> memberIds = new HashSet<>();
        personalityTargets.add(meId);
        for (PostSummaryDto post : posts) {
            personalityTargets.add(post.authorId());
            List<Long> members = partyByPost.getOrDefault(post.id(), List.of());
            personalityTargets.addAll(members);
            memberIds.addAll(members);
        }

        Map<Long, PersonalityProfile> personalities =
                userClient.fetchPersonalities(new ArrayList<>(personalityTargets));
        Map<Long, JsonNode> memberProfiles = userClient.fetchUsersByIds(new ArrayList<>(memberIds));

        UserSummaryDto meWithPersonality = me.withPersonality(personalities.get(meId));

        List<PostSummaryDto> enrichedPosts = posts.stream()
                .map(post -> {
                    List<PartyMemberDto> members = partyByPost.getOrDefault(post.id(), List.of()).stream()
                            .map(id -> toPartyMember(id, memberProfiles.get(id), personalities.get(id)))
                            .toList();
                    return post.withParty(personalities.get(post.authorId()), members);
                })
                .toList();

        return new Enriched(meWithPersonality, enrichedPosts);
    }

    private PartyMemberDto toPartyMember(Long userId, JsonNode profile, PersonalityProfile personality) {
        if (profile == null) {
            return new PartyMemberDto(userId, null, null, null, null, null, personality);
        }
        Integer age = (profile.path("age").isMissingNode() || profile.path("age").isNull())
                ? null : profile.path("age").asInt();
        // 시간대 3종도 같이 담는다 — /internal/users 응답(UserDto)에 이미 들어 있는데
        // 여기서 버리고 있어서 "플레이 시간대" 축이 파티원 쪽을 못 보고 있었다.
        return new PartyMemberDto(userId,
                profile.path("nickname").asText(null),
                age,
                profile.path("playTimes").asText(null),
                profile.path("playDays").asText(null),
                profile.path("playDuration").asText(null),
                personality);
    }

    /**
     * 응답용 파티 구성. 방장이 먼저, 그 뒤에 확정 파티원.
     *
     * 성향 점수는 빼고 닉네임·나이만 싣는다(PartyBrief 주석 참고). surveyed 는
     * "이 사람 몫의 점수가 실제 설문에 근거한 값인가"라서, 화면이 "3명 중 2명 설문
     * 완료"처럼 점수의 신뢰도를 설명할 수 있게 한다.
     */
    private List<PartyBrief> toPartyBriefs(PostSummaryDto post) {
        List<PartyBrief> briefs = new ArrayList<>();
        briefs.add(new PartyBrief(
                post.authorId(),
                post.authorNickname(),
                post.authorAge(),
                true,
                post.authorPersonality() != null && post.authorPersonality().hasAnyAxis()));

        if (post.members() != null) {
            for (PartyMemberDto m : post.members()) {
                briefs.add(new PartyBrief(
                        m.userId(),
                        m.nickname(),
                        m.age(),
                        false,
                        m.personality() != null && m.personality().hasAnyAxis()));
            }
        }
        return briefs;
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
