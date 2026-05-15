package com.capstone.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StudentFeedbackRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        String comment
) {
}
