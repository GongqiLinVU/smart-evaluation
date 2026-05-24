package com.capstone.eval.dto;

import com.capstone.eval.model.LlmConfig;

import java.time.LocalDateTime;

public record LlmConfigResponse(
        Long id,
        String name,
        String systemPromptTemplate,
        String additionalContext,
        String outputFormatTemplate,
        Double temperature,
        Integer maxTokens,
        String provider,
        String model,
        Boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LlmConfigResponse fromEntity(LlmConfig config) {
        return new LlmConfigResponse(
                config.getId(),
                config.getName(),
                config.getSystemPromptTemplate(),
                config.getAdditionalContext(),
                config.getOutputFormatTemplate(),
                config.getTemperature(),
                config.getMaxTokens(),
                config.getProvider(),
                config.getModel(),
                config.getIsDefault(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
