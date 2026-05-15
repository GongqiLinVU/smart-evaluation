package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String academicYear,
        @NotBlank String semester,
        String description
) {
}
