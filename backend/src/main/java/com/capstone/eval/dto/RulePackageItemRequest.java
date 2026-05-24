package com.capstone.eval.dto;

import jakarta.validation.constraints.NotNull;

public record RulePackageItemRequest(
        @NotNull Long ruleId,
        @NotNull Boolean enabled,
        @NotNull Double weight
) {}
