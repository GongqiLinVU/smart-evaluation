package com.capstone.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScoreAdjustmentRequest(
        @NotNull @Min(0) @Max(30) Integer adjustedScore,
        String reason
) {
}
