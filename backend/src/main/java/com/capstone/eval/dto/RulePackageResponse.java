package com.capstone.eval.dto;

import com.capstone.eval.model.RulePackage;

import java.time.LocalDateTime;
import java.util.List;

public record RulePackageResponse(
        Long id,
        String name,
        String description,
        Boolean isDefault,
        Boolean logicEnabled,
        Boolean methodologyEnabled,
        Boolean implementationEnabled,
        Double logicWeight,
        Double methodologyWeight,
        Double implementationWeight,
        List<RulePackageItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RulePackageResponse fromEntity(RulePackage rp) {
        List<RulePackageItemResponse> itemResponses = rp.getItems() != null
                ? rp.getItems().stream().map(RulePackageItemResponse::fromEntity).toList()
                : List.of();

        return new RulePackageResponse(
                rp.getId(),
                rp.getName(),
                rp.getDescription(),
                rp.getIsDefault(),
                rp.getLogicEnabled(),
                rp.getMethodologyEnabled(),
                rp.getImplementationEnabled(),
                rp.getLogicWeight(),
                rp.getMethodologyWeight(),
                rp.getImplementationWeight(),
                itemResponses,
                rp.getCreatedAt(),
                rp.getUpdatedAt()
        );
    }
}
