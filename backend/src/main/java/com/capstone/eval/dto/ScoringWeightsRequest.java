package com.capstone.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScoringWeightsRequest(
        @NotNull @Min(0) @Max(100) Double ruleBasedWeight,
        @NotNull @Min(0) @Max(100) Double llmWeight,
        @NotNull @Min(0) @Max(100) Double tutorWeight,
        @Min(1) @Max(200) Integer maxScore
) {
}
