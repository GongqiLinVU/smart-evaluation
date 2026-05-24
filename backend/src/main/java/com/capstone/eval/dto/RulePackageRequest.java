package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RulePackageRequest(
        @NotBlank String name,
        String description,
        @NotNull Boolean logicEnabled,
        @NotNull Boolean methodologyEnabled,
        @NotNull Boolean implementationEnabled,
        @NotNull Double logicWeight,
        @NotNull Double methodologyWeight,
        @NotNull Double implementationWeight,
        Boolean isDefault,
        List<RulePackageItemRequest> items
) {}
