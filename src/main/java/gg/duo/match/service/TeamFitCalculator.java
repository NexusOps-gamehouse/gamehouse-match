package gg.duo.match.service;

import gg.duo.match.dto.FitAxis;
import gg.duo.match.dto.PartyMemberDto;
import gg.duo.match.dto.PersonalityProfile;
import gg.duo.match.dto.PostSummaryDto;
import gg.duo.match.dto.UserSummaryDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Team Fit v3 스코어링.
 *
 * "파티"는 모집글 작성자(방장) + 이미 참여 확정(CONFIRMED)된 파티원이다. 방장 혼자면
 * 1명짜리 파티가 되고, 팀원이 둘 있으면 방장 포함 3명 전원이 계산에 들어간다.
 *
 * 포지션/티어는 여기서 다루지 않는다 — HardFilterService 가 이미 걸러낸 후보만
 * 이 계산기로 들어온다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * v2 → v3 에서 바뀐 것
 * ─────────────────────────────────────────────────────────────────────────
 * 1) 파티 평균이 아니라 **구성원별 점수의 평균**으로 계산한다.
 *    v2 는 축마다 파티 평균을 먼저 구하고 나와 비교했다. 그러면 승리 지향성 100인
 *    방장과 0인 팀원의 평균이 50이 되어, 50인 내가 "완벽한 궁합"인 100점을 받았다.
 *    실제로는 둘 중 누구와도 맞지 않는데도. 사람마다 따로 재서 평균 내면 그 상쇄가
 *    사라진다(위 예시는 50 → 50점, 정확히 절반만 맞는 상태로 나온다).
 *
 * 2) 값을 모르는 사람은 평균에서 **뺀다**.
 *    v2 는 설문 미응답자를 중립값으로 채워 평균에 섞었다. 나와 방장이 둘 다 100인데
 *    설문 안 한 팀원이 하나 끼면 점수가 떨어졌다 — 그건 궁합 정보가 아니라 결측이다.
 *    지금은 아는 사람끼리만 비교하고, 아무도 모를 때만 중립으로 떨어진다. 대신
 *    "몇 명이 설문을 했는지"를 surveyedCount 로 내보내 화면이 신뢰도를 말한다.
 *
 * 3) 주도성을 **양방향**으로 본다.
 *    PlayStyleAxis 주석이 설계로 못 박은 건 "한쪽 INITIATIVE 높음 + 다른 쪽
 *    INITIATIVE_PREFERENCE 높음이 가장 잘 맞는 짝"이다. v2 는 그중 절반(내 선호 vs
 *    상대 주도성)만 계산해서, 내가 이끌고 상대는 따라가고 싶어 하는 — 설계가 말한
 *    바로 그 최적 조합 — 이 점수에 전혀 반영되지 않았다.
 *
 * 4) 시간대 축이 생겼다(배점 15).
 *    회원가입에서 playTimes/playDays/playDuration 을 받아 두고도 매칭에 한 번도 쓰지
 *    않았다. constants.js 가 "파티가 깨지는 흔한 이유라 따로 받는다"고 적어둔 값이
 *    정작 배점 0이었다. 성향이 아무리 맞아도 접속 시간이 안 겹치면 같이 못 한다.
 *
 * 5) 음성 채팅 축이 생겼다(배점 5).
 *    voiceChat=PREFERRED("있으면 좋음")가 하드 필터에서는 ANY 와 완전히 같아서
 *    3단계로 나눈 의미가 없었다. constants.js 설명대로 "필수=하드 필터, 있으면
 *    좋음=소프트 점수"가 되도록 가산점 축을 둔다.
 *
 * 정규화 상수(MAX_AXIS_DIFF, 나이 감점 곡선, 분산 보정 폭)는 실사용 데이터가
 * 없는 상태에서 잡은 값이다 — STORY 03(Team Fit Scoring Policy)에서 실측
 * 데이터로 보정이 필요하다.
 */
@Component
public class TeamFitCalculator {

    // ── 배점(합계 100) ───────────────────────────────────────────────────
    // v2 의 성향 6축 + 나이 = 100 에서, 시간대(15)와 음성(5)이 들어올 자리를
    // 기존 축들에서 비례로 덜어냈다. 축 간 상대 순서는 회의록 표 그대로다.
    private static final double W_WIN_INTENT = 20;
    private static final double W_COMMUNICATION = 16;
    private static final double W_LEADERSHIP = 12;
    private static final double W_MISTAKE_TOLERANCE = 8;
    private static final double W_FOCUS = 8;
    private static final double W_SOCIABILITY = 8;
    private static final double W_AGE = 8;
    private static final double W_SCHEDULE = 15;
    private static final double W_VOICE = 5;

    /**
     * 설문 축은 0~100 스케일이다(PlayStyleAxis.score: 1점=0, 3점=50, 5점=100).
     * 두 사람이 가질 수 있는 최대 차이는 100 이다.
     *
     * v2 는 이 값이 80 이었다. "1~5점을 20배 해서 20~100"이라는 잘못된 전제에서
     * 나온 숫자인데, 분모가 실제보다 작아 모든 축의 유사도가 과하게 깎였고
     * 차이 80 이상은 전부 0점으로 뭉개져 구분 자체가 사라졌다(85와 100이 동점).
     */
    private static final double MAX_AXIS_DIFF = 100.0;

    // 실수 관용도: 파티 안에 극단적으로 낮은(50 미만) 사람이 있으면 유사도 평균에서
    // 추가 감점한다. 50에서 0까지 갈수록 최대 20점까지 깎는다.
    private static final double MISTAKE_EXTREME_THRESHOLD = 50.0;
    private static final double MISTAKE_EXTREME_MAX_PENALTY = 20.0;

    // 나이: ±3살 이내는 만점, 그 밖은 1살마다 10점씩 감점.
    private static final int AGE_TOLERANCE_YEARS = 3;
    private static final double AGE_PENALTY_PER_YEAR = 10.0;
    private static final double AGE_NEUTRAL_SCORE = 50.0; // 나이 정보가 없으면 중립값(가점도 감점도 아님)

    // Team Balance ±α: 파티 내 성향 편차가 크면 최대 5점까지 보정.
    private static final double MAX_BALANCE_PENALTY = 5.0;

    // 시간대: 요일·시간대는 겹치는 비율, 1회 분량은 단계 차이로 감점.
    private static final double SCHEDULE_NEUTRAL_SCORE = 50.0;
    private static final double DURATION_PENALTY_PER_STEP = 25.0;
    /** 프론트 constants.js PLAY_DURATIONS 와 같은 순서여야 한다. */
    private static final List<String> DURATION_ORDER =
            List.of("1~2시간", "2~4시간", "4~6시간", "6시간 이상");
    private static final String ANY_LABEL = "상관없음";

    public TeamFitResult calculate(UserSummaryDto me, PostSummaryDto post) {
        List<Member> party = buildParty(post);
        PersonalityProfile mine = me.personality() == null ? PersonalityProfile.empty() : me.personality();
        Schedule mySchedule = Schedule.of(me.playTimes(), me.playDays(), me.playDuration());

        List<FitAxis> axes = new ArrayList<>(List.of(
                axis("승리 지향성", axisScore(party, mine.winIntent(), m -> m.personality().winIntent()), W_WIN_INTENT),
                axis("소통 적극성", axisScore(party, mine.communication(), m -> m.personality().communication()), W_COMMUNICATION),
                axis("주도성", leadershipScore(mine, party), W_LEADERSHIP),
                axis("실수 관용도", mistakeToleranceScore(mine, party), W_MISTAKE_TOLERANCE),
                axis("플레이 집중도", axisScore(party, mine.focus(), m -> m.personality().focus()), W_FOCUS),
                axis("친목 성향", axisScore(party, mine.sociability(), m -> m.personality().sociability()), W_SOCIABILITY),
                axis("플레이 시간대", scheduleScore(mySchedule, party), W_SCHEDULE),
                axis("음성 채팅", voiceScore(me.mic(), post), W_VOICE),
                axis("나이", ageScore(me.age(), party), W_AGE)
        ));
        // 기여도 내림차순 — ExplanationService가 "배열 앞쪽일수록 크게 기여" 규칙으로 그대로 사용한다.
        axes.sort((a, b) -> Double.compare(b.contribution(), a.contribution()));

        double baseTotal = axes.stream().mapToDouble(FitAxis::contribution).sum();
        double total = clamp(baseTotal - balancePenalty(party), 0, 100);

        int surveyed = (int) party.stream().filter(m -> m.personality().hasAnyAxis()).count();
        return new TeamFitResult(round(total), axes, party.size(), surveyed);
    }

    private FitAxis axis(String label, AxisScore score, double weight) {
        double s = round(score.value());
        return new FitAxis(label, s, weight, round(s * weight / 100), score.known());
    }

    /**
     * 축 하나의 계산 결과.
     *
     * @param value 0~100 점수
     * @param known 실제 비교로 나온 값인가(false 면 데이터가 없어 중립값으로 채운 자리다).
     *              FitAxis.known 주석 참고 — "모른다"와 "안 맞는다"를 구분하기 위한 값이다.
     */
    private record AxisScore(double value, boolean known) {
        static AxisScore of(double value) {
            return new AxisScore(value, true);
        }

        static AxisScore unknown(double neutral) {
            return new AxisScore(neutral, false);
        }
    }

    // ── 파티 구성 ────────────────────────────────────────────────────────

    /** 방장이 0번, 확정 파티원이 그 뒤. 이후 계산은 둘을 구분하지 않는다. */
    private List<Member> buildParty(PostSummaryDto post) {
        List<Member> party = new ArrayList<>();
        party.add(new Member(
                post.authorPersonality() == null ? PersonalityProfile.empty() : post.authorPersonality(),
                post.authorAge(),
                Schedule.of(post.authorPlayTimes(), post.authorPlayDays(), post.authorPlayDuration())
        ));
        if (post.members() != null) {
            for (PartyMemberDto member : post.members()) {
                party.add(new Member(
                        member.personality() == null ? PersonalityProfile.empty() : member.personality(),
                        member.age(),
                        Schedule.of(member.playTimes(), member.playDays(), member.playDuration())
                ));
            }
        }
        return party;
    }

    private record Member(PersonalityProfile personality, Integer age, Schedule schedule) {
    }

    // ── 축별 계산 ────────────────────────────────────────────────────────

    /**
     * 성향 축 하나의 점수. **사람마다 따로 유사도를 재서 평균**낸다.
     *
     * 파티 평균을 먼저 구하면 100인 사람과 0인 사람이 서로를 상쇄해 50이 되고,
     * 50인 나는 만점을 받는다. 파티 인원이 늘수록 이 왜곡이 커진다.
     *
     * 내 값이나 상대 값이 없으면(설문 미응답) 그 쌍은 계산에서 뺀다. 비교할 쌍이
     * 하나도 없으면 중립값 — "모른다"를 감점으로 바꾸지 않는다.
     */
    private AxisScore axisScore(List<Member> party, Double myValue, Function<Member, Double> axisFn) {
        if (myValue == null) return AxisScore.unknown(PersonalityProfile.NEUTRAL);
        OptionalDouble avg = party.stream()
                .map(axisFn)
                .filter(Objects::nonNull)
                .mapToDouble(theirs -> similarity(myValue, theirs))
                .average();
        return avg.isPresent() ? AxisScore.of(avg.getAsDouble())
                : AxisScore.unknown(PersonalityProfile.NEUTRAL);
    }

    /**
     * 주도성 — 유사도가 아니라 **상보성**이고, 양방향이다.
     *
     *   (a) 내가 선호하는 상대 주도성  ↔  상대의 실제 주도성
     *   (b) 상대가 선호하는 주도성      ↔  나의 실제 주도성
     *
     * v2 는 (a)만 계산했다. 그래서 "내가 이끌고 상대는 따라가고 싶다"는 조합이
     * 점수에 전혀 잡히지 않았는데, PlayStyleAxis 주석이 최적 조합이라고 명시한 게
     * 정확히 그 경우다. 둘 다 재서 평균 낸다 — 한쪽 값이 없으면 있는 쪽만 쓴다.
     */
    private AxisScore leadershipScore(PersonalityProfile mine, List<Member> party) {
        List<Double> scores = new ArrayList<>();
        for (Member m : party) {
            PersonalityProfile theirs = m.personality();
            if (mine.leadershipPreference() != null && theirs.leadership() != null) {
                scores.add(similarity(mine.leadershipPreference(), theirs.leadership()));
            }
            if (theirs.leadershipPreference() != null && mine.leadership() != null) {
                scores.add(similarity(theirs.leadershipPreference(), mine.leadership()));
            }
        }
        if (scores.isEmpty()) return AxisScore.unknown(PersonalityProfile.NEUTRAL);
        return AxisScore.of(scores.stream().mapToDouble(Double::doubleValue).average()
                .orElse(PersonalityProfile.NEUTRAL));
    }

    /**
     * 실수 관용도 — 구성원별 유사도 평균에서, 파티에 극단적으로 낮은 사람이 있으면
     * 추가 감점한다. 한 명만 심하게 예민해도 파티 분위기 전체가 그 사람에게 맞춰지기
     * 때문에 평균만으로는 잡히지 않는다.
     */
    private AxisScore mistakeToleranceScore(PersonalityProfile mine, List<Member> party) {
        AxisScore base = axisScore(party, mine.mistakeTolerance(), m -> m.personality().mistakeTolerance());
        OptionalDouble partyMin = party.stream()
                .map(m -> m.personality().mistakeTolerance())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .min();
        if (partyMin.isEmpty() || partyMin.getAsDouble() >= MISTAKE_EXTREME_THRESHOLD) {
            return new AxisScore(clamp(base.value(), 0, 100), base.known());
        }
        double extremePenalty = (MISTAKE_EXTREME_THRESHOLD - partyMin.getAsDouble())
                / MISTAKE_EXTREME_THRESHOLD * MISTAKE_EXTREME_MAX_PENALTY;
        // 극단값이 존재한다는 건 상대 쪽 데이터를 안다는 뜻이라, 내 값이 없어도 이 축은 근거가 있다.
        return new AxisScore(clamp(base.value() - extremePenalty, 0, 100), true);
    }

    /**
     * 나이 — 여기도 파티 평균이 아니라 사람별로 잰다.
     *
     * 20살과 40살로 이뤄진 파티의 평균은 30이라, 27살인 사람이 만점을 받았다.
     * 실제로는 양쪽 다 13살 차이다.
     */
    private AxisScore ageScore(Integer myAge, List<Member> party) {
        if (myAge == null) return AxisScore.unknown(AGE_NEUTRAL_SCORE);
        OptionalDouble avg = party.stream()
                .map(Member::age)
                .filter(Objects::nonNull)
                .mapToDouble(theirAge -> {
                    double diff = Math.abs(myAge - theirAge);
                    if (diff <= AGE_TOLERANCE_YEARS) return 100.0;
                    return clamp(100.0 - (diff - AGE_TOLERANCE_YEARS) * AGE_PENALTY_PER_YEAR, 0, 100);
                })
                .average();
        return avg.isPresent() ? AxisScore.of(avg.getAsDouble()) : AxisScore.unknown(AGE_NEUTRAL_SCORE);
    }

    /**
     * 플레이 시간대 — 요일·시간대는 "겹치는 비율", 1회 분량은 "단계 차이"로 본다.
     *
     * 세 항목을 따로 재서 있는 것끼리만 평균낸다. 프로필을 덜 채운 사람이 무조건
     * 손해 보지 않게 하기 위해서다(성향 축과 같은 원칙).
     */
    private AxisScore scheduleScore(Schedule mine, List<Member> party) {
        OptionalDouble avg = party.stream()
                .mapToDouble(m -> pairScheduleScore(mine, m.schedule()))
                .filter(v -> v >= 0)
                .average();
        // 양쪽 다 프로필의 시간대를 안 채웠으면 "안 맞는다"가 아니라 "모른다"다.
        return avg.isPresent() ? AxisScore.of(avg.getAsDouble()) : AxisScore.unknown(SCHEDULE_NEUTRAL_SCORE);
    }

    /** 두 사람의 시간대 궁합. 비교할 항목이 하나도 없으면 -1(계산에서 제외). */
    private double pairScheduleScore(Schedule mine, Schedule theirs) {
        List<Double> parts = new ArrayList<>();
        Double days = overlapRatio(mine.days(), theirs.days());
        if (days != null) parts.add(days);
        Double times = overlapRatio(mine.times(), theirs.times());
        if (times != null) parts.add(times);
        Double duration = durationScore(mine.duration(), theirs.duration());
        if (duration != null) parts.add(duration);

        if (parts.isEmpty()) return -1;
        return parts.stream().mapToDouble(Double::doubleValue).average().orElse(SCHEDULE_NEUTRAL_SCORE);
    }

    /**
     * 겹치는 비율. 분모는 더 적게 고른 쪽의 개수다.
     *
     * 합집합(자카드)으로 나누면 "매일 접속하는 사람"이 손해를 본다 — 요일을 7개
     * 고른 사람과 2개 고른 사람이 2개 겹치면 2/7 이 되어버린다. 실제로는 그 2개에
     * 둘 다 접속하므로 같이 할 수 있다.
     */
    private Double overlapRatio(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return null;                      // 한쪽이라도 모르면 비교 불가
        if (a.contains(ANY_LABEL) || b.contains(ANY_LABEL)) return 100.0; // "상관없음"은 언제든 가능

        long overlap = a.stream().filter(b::contains).count();
        return 100.0 * overlap / Math.min(a.size(), b.size());
    }

    /** 1회 플레이 분량 — 한 단계 차이마다 25점씩 깎는다. */
    private Double durationScore(String mine, String theirs) {
        int a = DURATION_ORDER.indexOf(mine == null ? "" : mine.trim());
        int b = DURATION_ORDER.indexOf(theirs == null ? "" : theirs.trim());
        if (a < 0 || b < 0) return null;
        return clamp(100.0 - Math.abs(a - b) * DURATION_PENALTY_PER_STEP, 0, 100);
    }

    /**
     * 음성 채팅 — 하드 필터를 통과한 뒤 남는 "있으면 좋음"의 무게를 여기서 준다.
     *
     * REQUIRED 는 HardFilterService 가 이미 걸렀지만, 결과가 너무 적어 완화 사다리가
     * 작동한 경우엔 마이크 없는 사람도 통과해 여기까지 온다. 그때는 0점을 줘서
     * 순위에서 뒤로 밀리게 한다 — 후보에서 지우지는 않되 우선순위는 낮추는 게
     * 완화의 취지다.
     */
    private AxisScore voiceScore(boolean iHaveMic, PostSummaryDto post) {
        String level = (post.micLevel() == null || post.micLevel().isBlank())
                ? (post.micRequired() ? "REQUIRED" : "ANY")
                : post.micLevel().trim().toUpperCase(Locale.ROOT);

        // 모집글은 항상 voiceChat 값을 갖고 마이크 보유 여부도 필수 입력이라, 이 축은 늘 근거가 있다.
        return AxisScore.of(switch (level) {
            case "REQUIRED" -> iHaveMic ? 100.0 : 0.0;
            case "PREFERRED" -> iHaveMic ? 100.0 : 40.0;
            default -> 100.0; // ANY — 조건이 없으니 이 축으로 불이익을 주지 않는다
        });
    }

    /**
     * Team Balance — 파티 내부의 성향 편차가 크면 감점한다.
     *
     * 구성원별 점수 평균(axisScore)이 v2 의 상쇄 문제를 대부분 없앴지만, "안에서
     * 서로 안 맞는 파티"라는 사실 자체는 여전히 정보다. 여섯 성향 축을 모두 본다 —
     * v2 는 넷만 봐서 주도성·실수 관용도의 편차가 빠져 있었다.
     */
    private double balancePenalty(List<Member> party) {
        if (party.size() < 2) return 0; // 비교 대상이 1명뿐이면 편차를 논할 수 없다

        List<Function<Member, Double>> axes = List.of(
                m -> m.personality().winIntent(),
                m -> m.personality().communication(),
                m -> m.personality().focus(),
                m -> m.personality().sociability(),
                m -> m.personality().leadership(),
                m -> m.personality().mistakeTolerance()
        );

        OptionalDouble avgStdDev = axes.stream()
                .mapToDouble(fn -> stdDev(party, fn))
                .filter(v -> v >= 0)
                .average();
        if (avgStdDev.isEmpty()) return 0;

        // 표준편차 10당 1점 감점, 최대 5점 — 1차 휴리스틱.
        return clamp(avgStdDev.getAsDouble() / 10.0, 0, MAX_BALANCE_PENALTY);
    }

    /** 값을 아는 구성원만으로 구한 표준편차. 두 명 미만이면 -1(계산에서 제외). */
    private double stdDev(List<Member> party, Function<Member, Double> axisFn) {
        List<Double> values = party.stream()
                .map(axisFn)
                .filter(Objects::nonNull)
                .toList();
        if (values.size() < 2) return -1;

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    private double similarity(double mine, double theirs) {
        double diff = Math.abs(mine - theirs);
        return clamp(100.0 - (diff / MAX_AXIS_DIFF) * 100.0, 0, 100);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** 프로필의 시간대 3종을 비교하기 좋은 형태로 정리한 값. */
    private record Schedule(Set<String> times, Set<String> days, String duration) {
        static Schedule of(String times, String days, String duration) {
            return new Schedule(splitCsv(times), splitCsv(days),
                    (duration == null || duration.isBlank()) ? null : duration.trim());
        }

        private static Set<String> splitCsv(String csv) {
            if (csv == null || csv.isBlank()) return Set.of();
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /**
     * @param partySize     계산에 들어간 인원(방장 포함)
     * @param surveyedCount 그중 성향 설문을 마친 인원. 점수의 신뢰도를 화면에서
     *                      설명하기 위한 값이다 — 미응답자는 감점 대신 이 숫자로 드러난다.
     */
    public record TeamFitResult(double total, List<FitAxis> axes, int partySize, int surveyedCount) {
    }
}
