package gg.duo.match.dto;

/**
 * 플레이 성향 설문(6영역 12문항) 결과를 축별로 집계한 값.
 *
 * [스케일] user 서비스의 PlayStyleAxis.score() 가 만드는 값을 그대로 담는다.
 *
 *     score = round((문항평균 - 1) / 4 * 100)     // 1점=0, 3점=50, 5점=100
 *
 * 즉 범위는 0~100 이고 중앙값은 50 이다. 예전 주석은 "1~5점을 20배"(20~100, 중앙 60)
 * 라고 적혀 있었지만 실제 저장 로직과 달랐고, 그 전제로 잡은 TeamFitCalculator 의
 * NEUTRAL·MAX_AXIS_DIFF 가 함께 틀려 모든 축 점수가 조용히 어긋나 있었다.
 * 이 숫자를 바꿀 때는 반드시 PlayStyleAxis.score() 를 먼저 확인할 것.
 *
 * 설문에 응답하지 않았거나 아직 상대(파티원)의 데이터를 못 가져온 경우 해당 축은
 * null 이다. null 은 "중립"이 아니라 "모름"이다 — TeamFitCalculator 는 모르는 값을
 * 평균에서 아예 빼고, 파티 전원이 모를 때만 중립값으로 떨어진다. 설문을 안 한
 * 사람이 파티에 있다는 이유로 궁합 점수가 깎이면 안 되기 때문이다.
 *
 * leadership 과 leadershipPreference 는 서로 다른 축이다 — 9번 문항(나의 주도성)과
 * 10번 문항(내가 선호하는 상대의 주도성)을 그대로 분리해서 담는다. 나머지 5개
 * 축은 문항 2개씩의 평균이다.
 */
public record PersonalityProfile(
        Double winIntent,            // 🏆 승리 지향성 (Q1,Q2 평균)
        Double mistakeTolerance,     // 🤝 실수 관용도 (Q3,Q4 평균)
        Double communication,        // 💬 소통 적극성 (Q5,Q6 평균)
        Double focus,                // 🎮 플레이 집중도 (Q7,Q8 평균)
        Double leadership,           // 🧭 나의 주도성 (Q9)
        Double leadershipPreference, // 🧭 선호하는 상대 주도성 (Q10)
        Double sociability           // 🧑‍🤝‍🧑 친목 성향 (Q11,Q12 평균)
) {
    /** 0~100 스케일의 중앙값. 1~5 리커트의 3점에 해당한다. */
    public static final double NEUTRAL = 50.0;

    /** 설문을 아예 안 한 사용자/파티원. 모든 축이 "모름"이다. */
    public static PersonalityProfile empty() {
        return new PersonalityProfile(null, null, null, null, null, null, null);
    }

    /** 축이 하나라도 채워져 있으면 true — "이 사람은 설문을 했다"의 판정. */
    public boolean hasAnyAxis() {
        return winIntent != null || mistakeTolerance != null || communication != null
                || focus != null || leadership != null || leadershipPreference != null
                || sociability != null;
    }

    private double orNeutral(Double v) {
        return v == null ? NEUTRAL : v;
    }

    public double winIntentOrNeutral() { return orNeutral(winIntent); }
    public double mistakeToleranceOrNeutral() { return orNeutral(mistakeTolerance); }
    public double communicationOrNeutral() { return orNeutral(communication); }
    public double focusOrNeutral() { return orNeutral(focus); }
    public double leadershipOrNeutral() { return orNeutral(leadership); }
    public double leadershipPreferenceOrNeutral() { return orNeutral(leadershipPreference); }
    public double sociabilityOrNeutral() { return orNeutral(sociability); }
}
