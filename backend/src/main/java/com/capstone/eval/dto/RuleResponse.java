package com.capstone.eval.dto;

import com.capstone.eval.model.Rule;

import java.time.LocalDateTime;

public record RuleResponse(
        Long id,
        String ruleKey,
        String name,
        String description,
        String category,
        String llmCriterionPrompt,
        Boolean builtIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RuleResponse fromEntity(Rule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getRuleKey(),
                rule.getName(),
                rule.getDescription(),
                rule.getCategory(),
                rule.getLlmCriterionPrompt(),
                rule.getBuiltIn(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
