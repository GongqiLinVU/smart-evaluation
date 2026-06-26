package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectTaskRequest(
        @NotBlank String name,
        String description,
        Integer displayOrder,
        Long rulePackageId,
        Long llmConfigId
) {
}
