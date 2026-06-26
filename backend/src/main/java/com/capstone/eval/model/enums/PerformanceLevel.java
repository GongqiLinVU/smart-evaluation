package com.capstone.eval.model.enums;

public enum PerformanceLevel {
    // 30-point scale (final report)
    EXCELLENT(30),
    PROFICIENT(24),
    COMPETENT(18),
    DEVELOPING(12),
    INADEQUATE(6),

    // 10-point scale (progress report)
    HD(10),
    D(8),
    C(6),
    P(4),
    F(2);

    private final int points;

    PerformanceLevel(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }

    public static PerformanceLevel fromRawScore(double rawScore) {
        if (rawScore >= 85) return EXCELLENT;
        if (rawScore >= 65) return PROFICIENT;
        if (rawScore >= 45) return COMPETENT;
        if (rawScore >= 25) return DEVELOPING;
        return INADEQUATE;
    }

    public static PerformanceLevel fromPoints(int points) {
        for (PerformanceLevel level : values()) {
            if (level.points == points) return level;
        }
        if (points >= 27) return EXCELLENT;
        if (points >= 21) return PROFICIENT;
        if (points >= 15) return COMPETENT;
        if (points >= 9) return DEVELOPING;
        return INADEQUATE;
    }

    public static PerformanceLevel fromPoints(int points, int[] validScores) {
        if (validScores == null || validScores.length == 0) {
            return fromPoints(points);
        }
        int maxScore = validScores[validScores.length - 1];
        if (maxScore <= 10) {
            if (points >= 10) return HD;
            if (points >= 8) return D;
            if (points >= 6) return C;
            if (points >= 4) return P;
            return F;
        }
        return fromPoints(points);
    }
}
