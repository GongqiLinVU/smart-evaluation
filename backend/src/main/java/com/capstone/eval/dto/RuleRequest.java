package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;

public record RuleRequest(
        @NotBlank String ruleKey,
        @NotBlank String name,
        String description,
        String category,
        String llmCriterionPrompt
) {}
