package com.capstone.eval.dto;

import java.util.List;

public record CompositeScoreResponse(
        Double compositeScore,
        Double compositePercentage,
        int maxScore,
        String level,
        List<ComponentScore> components,
        ScoringWeightsResponse weights
) {

    public record ComponentScore(
            String method,
            Integer rawScore,
            Integer rawMaxScore,
            Double percentage,
            Double weight,
            Double weightedContribution,
            boolean available
    ) {
    }
}
