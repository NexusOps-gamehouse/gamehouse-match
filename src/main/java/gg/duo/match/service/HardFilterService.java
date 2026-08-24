package gg.duo.match.service;

import gg.duo.match.dto.MatchSearchRequest;
import gg.duo.match.dto.PostSummaryDto;
import gg.duo.match.dto.UserSummaryDto;
import gg.duo.match.strategy.GameMatchingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 점수를 매기기 전에 애초에 후보가 될 수 없는 글을 걸러낸다.
 *
 * Team Fit v2부터 포지션/역할, 티어 범위도 Hard Filter다(회의록 "Team Fit 점수
 * 산정 방식" 표). 다만 완화 사다리는 기존 마이크 "호환성" 체크(모집글이 마이크를
 * 요구하는데 내가 마이크가 없는 경우)에만 적용한다 — /match/new에서 사용자가 직접
 * 고른 조건(포지션·마이크 선호·희망 인원)은 "필터링에 맞지 않는 조건은 아예
 * 계산하거나 보여주지 않는다"는 회의록 원칙에 따라 항상 엄격하게 적용하고 완화하지
 * 않는다.
 */
@Component
@RequiredArgsConstructor
public class HardFilterService {

    private static final int MIN_RESULTS_BEFORE_RELAX = 3;

    private final List<GameMatchingStrategy> strategies;

    public record Outcome(List<PostSummaryDto> posts, boolean relaxed) {
    }

    public Outcome filter(UserSummaryDto me, List<PostSummaryDto> candidates, MatchSearchRequest request) {
        GameMatchingStrategy strategy = strategyFor(request.game());
        List<String> searchPositions = effectivePositions(me, request);
        String myTier = effectiveTier(me, request);

        // 완화 대상이 아닌 조건들 — 이걸 만족 못 하면 어떤 경우에도 후보가 될 수 없다.
        List<PostSummaryDto> nonNegotiable = candidates.stream()
                .filter(post -> !me.id().equals(post.authorId()))
                .filter(post -> post.currentMembers() < post.targetMembers())
                .filter(post -> "RECRUITING".equals(post.status()))
                .filter(post -> strategy.positionMatches(searchPositions, post.positions()))
                .filter(post -> strategy.tierInRange(myTier, post.tierMin(), post.tierMax()))
                .filter(post -> matchesMicPreference(post, request))
                .filter(post -> matchesTargetMembers(post, request))
                .toList();

        List<PostSummaryDto> strict = nonNegotiable.stream()
                .filter(post -> !post.micRequired() || me.mic())
                .toList();

        if (strict.size() >= MIN_RESULTS_BEFORE_RELAX) {
            return new Outcome(strict, false);
        }

        // 완화 1단계: 마이크 "호환성"만 무시한다. 포지션/티어/희망인원/게임/모집상태/
        // 본인글 조건은 완화하지 않는다.
        return new Outcome(nonNegotiable, nonNegotiable.size() > strict.size());
    }

    private List<String> effectivePositions(UserSummaryDto me, MatchSearchRequest request) {
        if (request.positions() != null && !request.positions().isEmpty()) return request.positions();
        if (me.position() != null && !me.position().isBlank()) return List.of(me.position());
        return List.of(); // 상관없음
    }

    /**
     * request.tier()는 "이번 검색에서는 이 티어로 취급해줘"라는 검색 시점 오버라이드다
     * (예: 프로필 티어가 아직 없거나, 다른 티어대로 한번 둘러보고 싶을 때). 비어있으면
     * 기존처럼 프로필의 riotTier(검증됨) → tier(자기신고) 순으로 쓴다.
     */
    private String effectiveTier(UserSummaryDto me, MatchSearchRequest request) {
        if (request.tier() != null && !request.tier().isBlank()) return request.tier();
        return (me.riotTier() != null && !me.riotTier().isBlank()) ? me.riotTier() : me.tier();
    }

    /**
     * 마이크 선호는 micLevel(REQUIRED|PREFERRED|ANY)이 우선이고, 없으면 구버전
     * micRequired(Boolean)로 대체 판단한다. post 쪽도 마찬가지로 micLevel이 있으면
     * 그걸 쓰고, 없으면 micRequired(boolean)로 대체한다(PostClient/README 참고).
     */
    private boolean matchesMicPreference(PostSummaryDto post, MatchSearchRequest request) {
        String level = request.micLevel();
        if (level == null || level.isBlank()) {
            // 구버전 호환: micLevel이 없으면 micRequired(Boolean)로 판단
            return request.micRequired() == null || request.micRequired().booleanValue() == post.micRequired();
        }
        if ("ANY".equalsIgnoreCase(level)) return true;

        boolean postRequiresMic = (post.micLevel() != null && !post.micLevel().isBlank())
                ? "REQUIRED".equalsIgnoreCase(post.micLevel())
                : post.micRequired();

        if ("REQUIRED".equalsIgnoreCase(level)) return postRequiresMic;
        return true; // PREFERRED — 마이크 있으면 좋지만 없어도 후보에서 빼지 않는다
    }

    private boolean matchesTargetMembers(PostSummaryDto post, MatchSearchRequest request) {
        List<Integer> options = request.targetMembersOptions();
        return options == null || options.isEmpty() || options.contains(post.targetMembers());
    }

    /**
     * user/post 서비스(그리고 프론트 constants.js GAMES)는 게임을 한글 라벨("리그오브레전드")로
     * 저장·검색하는데, GameMatchingStrategy.gameCode()는 영문 코드("LOL")다. match만 새
     * 어휘를 요구하면 프론트/다른 서비스를 다 고쳐야 하므로, 여기서 한글 라벨을 코드로
     * 변환해 흡수한다 — request.game 값 자체는 PostClient가 그대로 post 서비스에 넘기므로
     * (한글 라벨이어야 실제 글이 검색됨) 원본 값은 건드리지 않고 전략 조회할 때만 정규화한다.
     */
    private static final Map<String, String> GAME_ALIASES = Map.of(
            "리그오브레전드", "LOL",
            "롤", "LOL",
            "발로란트", "VALORANT"
    );

    private GameMatchingStrategy strategyFor(String game) {
        String trimmed = game.trim();
        String normalized = GAME_ALIASES.getOrDefault(trimmed, trimmed);
        return strategies.stream()
                .filter(s -> s.gameCode().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 게임입니다: " + game));
    }
}
