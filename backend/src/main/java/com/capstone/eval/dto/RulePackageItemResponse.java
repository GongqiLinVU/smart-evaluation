package com.capstone.eval.dto;

import com.capstone.eval.model.RulePackageItem;

public record RulePackageItemResponse(
        Long id,
        Long ruleId,
        String ruleKey,
        String ruleName,
        String ruleCategory,
        Boolean enabled,
        Double weight
) {
    public static RulePackageItemResponse fromEntity(RulePackageItem item) {
        return new RulePackageItemResponse(
                item.getId(),
                item.getRule().getId(),
                item.getRule().getRuleKey(),
                item.getRule().getName(),
                item.getRule().getCategory(),
                item.getEnabled(),
                item.getWeight()
        );
    }
}
