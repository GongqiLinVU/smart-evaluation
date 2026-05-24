package com.capstone.eval.dto;

public record ScoringWeightsResponse(
        double ruleBasedWeight,
        double llmWeight,
        double tutorWeight,
        int maxScore
) {
}
