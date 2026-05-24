package com.capstone.eval.model.enums;

public enum PerformanceLevel {
    EXCELLENT(30),
    PROFICIENT(24),
    COMPETENT(18),
    DEVELOPING(12),
    INADEQUATE(6);

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
}
