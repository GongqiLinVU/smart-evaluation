package com.capstone.eval.dto;

import com.capstone.eval.model.RulePackageItem;

public record RulePackageItemResponse(
        Long id,
        Long ruleId,
        String ruleKey,
        String ruleName,
        String ruleCategory,
        Boolean enabled,
        Double weight,
        Integer maxPoints,
        Boolean hasEvidenceQuestions,
        Boolean hasScoringRules
) {
    public static RulePackageItemResponse fromEntity(RulePackageItem item) {
        return new RulePackageItemResponse(
                item.getId(),
                item.getRule().getId(),
                item.getRule().getRuleKey(),
                item.getRule().getName(),
                item.getRule().getCategory(),
                item.getEnabled(),
                item.getWeight(),
                item.getMaxPoints(),
                item.getEvidenceQuestions() != null && !item.getEvidenceQuestions().isBlank(),
                item.getScoringRules() != null && !item.getScoringRules().isBlank()
        );
    }
}
