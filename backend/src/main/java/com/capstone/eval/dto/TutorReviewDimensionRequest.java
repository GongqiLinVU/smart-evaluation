package com.capstone.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TutorReviewDimensionRequest(
        @NotBlank String dimensionName,
        @NotNull @Min(0) @Max(10) Integer score,
        @NotNull @Min(1) @Max(10) Integer maxScore,
        String justification
) {
}
