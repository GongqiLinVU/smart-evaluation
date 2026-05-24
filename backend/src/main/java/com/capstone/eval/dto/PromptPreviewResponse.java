package com.capstone.eval.dto;

import java.util.List;

public record PromptPreviewResponse(
        String systemPrompt,
        int estimatedTokens,
        List<String> criteriaIncluded
) {}
