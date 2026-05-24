package com.capstone.eval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.util.List;

public record TutorReviewRequest(
        @NotNull @Min(0) @Max(30) Integer overallScore,
        String overallComment,
        @NotEmpty @Valid List<TutorReviewDimensionRequest> dimensions
) {
}
