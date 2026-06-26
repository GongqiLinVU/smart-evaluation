package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;

public record LlmConfigRequest(
        @NotBlank String name,
        String systemPromptTemplate,
        String additionalContext,
        String outputFormatTemplate,
        Double temperature,
        Integer maxTokens,
        String provider,
        String model,
        Boolean multimodal,
        Boolean isDefault
) {}
