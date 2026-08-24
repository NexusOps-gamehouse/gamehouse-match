package gg.duo.match.service;

import gg.duo.match.dto.FitAxis;
import gg.duo.match.dto.PartyMemberDto;
import gg.duo.match.dto.PersonalityProfile;
import gg.duo.match.dto.PostSummaryDto;
import gg.duo.match.dto.UserSummaryDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/**
 * Team Fit v2 스코어링. 회의록 "Team Fit 점수 산정 방식" 표를 그대로 구현한다.
 * 포지션/티어는 더 이상 여기서 다루지 않는다 — HardFilterService가 이미 걸러낸
 * 후보만 이 계산기로 들어온다.
 *
 * "파티"는 모집글 작성자 + 이미 참여 확정된 파티원(PostSummaryDto.members)이다.
 * post 서비스가 아직 members를 못 내려주면 작성자 한 명이 곧 파티 전체로 계산된다
 * (README 참고) — 데이터가 채워지면 코드 변경 없이 자동으로 다인원 평균 계산으로
 * 바뀐다.
 *
 * 정규화 상수(MAX_AXIS_DIFF, 나이 감점 곡선, 분산 보정 폭)는 실사용 데이터가
 * 없는 상태에서 잡은 1차 값이다 — STORY 03(Team Fit Scoring Policy)에서 실측
 * 데이터로 보정이 필요하다.
 */
@Component
public class TeamFitCalculator {

    // 배점(회의록 Team Fit 표, 합계 100)
    private static final double W_WIN_INTENT = 25;
    private static final double W_COMMUNICATION = 20;
    private static final double W_LEADERSHIP = 15;
    private static final double W_MISTAKE_TOLERANCE = 10;
    private static final double W_FOCUS = 10;
    private static final double W_SOCIABILITY = 10;
    private static final double W_AGE = 10;

    // 설문 축은 1~5점을 20배 해서 0~100으로 담기로 했으므로(PersonalityProfile 참고),
    // 두 사람이 가질 수 있는 최대 차이는 80이다. 이 값으로 나눠 0~100 유사도로 바꾼다.
    private static final double MAX_AXIS_DIFF = 80.0;

    // 실수 관용도: 파티 안에 극단적으로 낮은(50 미만) 사람이 있으면 평균 유사도에서
    // 추가 감점한다. 50에서 0까지 갈수록 최대 20점까지 깎는다.
    private static final double MISTAKE_EXTREME_THRESHOLD = 50.0;
    private static final double MISTAKE_EXTREME_MAX_PENALTY = 20.0;

    // 나이: ±3살 이내는 만점, 그 밖은 1살마다 10점씩 감점.
    private static final int AGE_TOLERANCE_YEARS = 3;
    private static final double AGE_PENALTY_PER_YEAR = 10.0;
    private static final double AGE_NEUTRAL_SCORE = 50.0; // 나이 정보가 없으면 중립값(가점도 감점도 아님)

    // Team Balance ±α: 파티 내 성향 편차가 크면 최대 5점까지 보정.
    private static final double MAX_BALANCE_PENALTY = 5.0;

    public TeamFitResult calculate(UserSummaryDto me, PostSummaryDto post) {
        List<PartyMemberEntry> party = buildParty(post);
        PersonalityProfile mine = personalityOf(me);

        double winIntent = similarity(mine.winIntentOrNeutral(), avg(party, PartyMemberEntry::winIntent));
        double communication = similarity(mine.communicationOrNeutral(), avg(party, PartyMemberEntry::communication));
        double focus = similarity(mine.focusOrNeutral(), avg(party, PartyMemberEntry::focus));
        double sociability = similarity(mine.sociabilityOrNeutral(), avg(party, PartyMemberEntry::sociability));
        // 주도성은 유사도가 아니다 — "내가 선호하는 상대 주도성" vs "파티의 실제 주도성"을 비교한다.
        double leadership = similarity(mine.leadershipPreferenceOrNeutral(), avg(party, PartyMemberEntry::leadership));
        double mistakeTolerance = mistakeToleranceScore(mine.mistakeToleranceOrNeutral(), party);
        double age = ageScore(me.age(), party);

        List<FitAxis> axes = new ArrayList<>(List.of(
                axis("승리 지향성", winIntent, W_WIN_INTENT),
                axis("소통 적극성", communication, W_COMMUNICATION),
                axis("주도성", leadership, W_LEADERSHIP),
                axis("실수 관용도", mistakeTolerance, W_MISTAKE_TOLERANCE),
                axis("플레이 집중도", focus, W_FOCUS),
                axis("친목 성향", sociability, W_SOCIABILITY),
                axis("나이", age, W_AGE)
        ));
        // 기여도 내림차순 — ExplanationService가 "배열 앞쪽일수록 크게 기여" 규칙으로 그대로 사용한다.
        axes.sort((a, b) -> Double.compare(b.contribution(), a.contribution()));

        double baseTotal = axes.stream().mapToDouble(FitAxis::contribution).sum();
        double total = clamp(baseTotal - balancePenalty(party), 0, 100);

        return new TeamFitResult(round(total), axes, party.size());
    }

    private FitAxis axis(String label, double score, double weight) {
        double s = round(score);
        return new FitAxis(label, s, weight, round(s * weight / 100));
    }

    // --- 파티 구성 ---

    private List<PartyMemberEntry> buildParty(PostSummaryDto post) {
        List<PartyMemberEntry> party = new ArrayList<>();
        party.add(new PartyMemberEntry(
                post.authorPersonality() == null ? PersonalityProfile.empty() : post.authorPersonality(),
                post.authorAge()
        ));
        if (post.members() != null) {
            for (PartyMemberDto member : post.members()) {
                party.add(new PartyMemberEntry(
                        member.personality() == null ? PersonalityProfile.empty() : member.personality(),
                        member.age()
                ));
            }
        }
        return party;
    }

    private record PartyMemberEntry(PersonalityProfile personality, Integer age) {
        double winIntent() { return personality.winIntentOrNeutral(); }
        double communication() { return personality.communicationOrNeutral(); }
        double focus() { return personality.focusOrNeutral(); }
        double sociability() { return personality.sociabilityOrNeutral(); }
        double leadership() { return personality.leadershipOrNeutral(); } // "본인의 주도성" — 파티의 실제 주도성 비교에 쓴다
        double mistakeTolerance() { return personality.mistakeToleranceOrNeutral(); }
    }

    private PersonalityProfile personalityOf(UserSummaryDto me) {
        return me.personality() == null ? PersonalityProfile.empty() : me.personality();
    }

    // --- 축별 계산 ---

    private double similarity(double mine, double partyAvg) {
        double diff = Math.abs(mine - partyAvg);
        return clamp(100.0 - (diff / MAX_AXIS_DIFF) * 100.0, 0, 100);
    }

    private double mistakeToleranceScore(double mine, List<PartyMemberEntry> party) {
        double base = similarity(mine, avg(party, PartyMemberEntry::mistakeTolerance));
        double partyMin = party.stream()
                .mapToDouble(PartyMemberEntry::mistakeTolerance)
                .min()
                .orElse(PersonalityProfile.NEUTRAL);
        double extremePenalty = partyMin < MISTAKE_EXTREME_THRESHOLD
                ? (MISTAKE_EXTREME_THRESHOLD - partyMin) / MISTAKE_EXTREME_THRESHOLD * MISTAKE_EXTREME_MAX_PENALTY
                : 0;
        return clamp(base - extremePenalty, 0, 100);
    }

    private double ageScore(Integer myAge, List<PartyMemberEntry> party) {
        OptionalDouble partyAvgAge = party.stream()
                .filter(p -> p.age() != null)
                .mapToInt(PartyMemberEntry::age)
                .average();
        if (myAge == null || partyAvgAge.isEmpty()) return AGE_NEUTRAL_SCORE;

        double diff = Math.abs(myAge - partyAvgAge.getAsDouble());
        if (diff <= AGE_TOLERANCE_YEARS) return 100.0;
        return clamp(100.0 - (diff - AGE_TOLERANCE_YEARS) * AGE_PENALTY_PER_YEAR, 0, 100);
    }

    private double balancePenalty(List<PartyMemberEntry> party) {
        if (party.size() < 2) return 0; // 비교 대상이 1명뿐이면 편차를 논할 수 없다
        double avgStdDev = (
                stdDev(party, PartyMemberEntry::winIntent) +
                stdDev(party, PartyMemberEntry::communication) +
                stdDev(party, PartyMemberEntry::focus) +
                stdDev(party, PartyMemberEntry::sociability)
        ) / 4.0;
        // 표준편차 10당 1점 감점, 최대 5점 — 1차 휴리스틱.
        return clamp(avgStdDev / 10.0, 0, MAX_BALANCE_PENALTY);
    }

    private double avg(List<PartyMemberEntry> party, ToDoubleFunction<PartyMemberEntry> axisFn) {
        return party.stream().mapToDouble(axisFn).average().orElse(PersonalityProfile.NEUTRAL);
    }

    private double stdDev(List<PartyMemberEntry> party, ToDoubleFunction<PartyMemberEntry> axisFn) {
        double mean = avg(party, axisFn);
        double variance = party.stream()
                .mapToDouble(p -> Math.pow(axisFn.applyAsDouble(p) - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    public record TeamFitResult(double total, List<FitAxis> axes, int partySize) {
    }
}
