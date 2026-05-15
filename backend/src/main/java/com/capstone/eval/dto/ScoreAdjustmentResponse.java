package com.capstone.eval.dto;

import com.capstone.eval.model.ScoreAdjustment;

import java.time.LocalDateTime;

public record ScoreAdjustmentResponse(
        Long id,
        Long evaluationId,
        String tutorName,
        Integer originalScore,
        Integer adjustedScore,
        String reason,
        LocalDateTime adjustedAt
) {
    public static ScoreAdjustmentResponse fromEntity(ScoreAdjustment adj) {
        return new ScoreAdjustmentResponse(
                adj.getId(),
                adj.getEvaluation().getId(),
                adj.getTutor().getFullName(),
                adj.getOriginalScore(),
                adj.getAdjustedScore(),
                adj.getReason(),
                adj.getAdjustedAt()
        );
    }
}
