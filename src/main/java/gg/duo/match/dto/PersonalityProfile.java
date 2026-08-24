package gg.duo.match.dto;

/**
 * 플레이 성향 설문(6영역 12문항) 결과를 축별로 집계한 값. 각 축은 0~100 스케일
 * (문항 1~5점 응답의 평균 * 20)로 정규화해서 담는다. 설문에 응답하지 않았거나
 * 아직 상대(파티원)의 설문 데이터를 못 가져온 경우 해당 축은 null일 수 있고,
 * TeamFitCalculator는 null을 중립값(60.0)으로 취급한다 — 설문을 안 했다고
 * 매칭 계산 자체가 실패하지 않는다.
 *
 * leadership과 leadershipPreference는 서로 다른 축이다 — 9번 문항(나의 주도성)과
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
    public static final double NEUTRAL = 60.0;

    /** 설문을 아예 안 한 사용자/파티원을 표현할 때 쓴다. 모든 축이 중립값으로 취급된다. */
    public static PersonalityProfile empty() {
        return new PersonalityProfile(null, null, null, null, null, null, null);
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
