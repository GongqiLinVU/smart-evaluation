package com.capstone.eval.dto;

import com.capstone.eval.model.TutorReviewDimension;

public record TutorReviewDimensionResponse(
        Long id,
        String dimensionName,
        Integer score,
        Integer maxScore,
        String justification
) {
    public static TutorReviewDimensionResponse fromEntity(TutorReviewDimension dim) {
        return new TutorReviewDimensionResponse(
                dim.getId(),
                dim.getDimensionName(),
                dim.getScore(),
                dim.getMaxScore(),
                dim.getJustification()
        );
    }
}
