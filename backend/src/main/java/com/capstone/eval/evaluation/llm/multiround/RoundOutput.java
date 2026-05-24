package com.capstone.eval.evaluation.llm.multiround;

import com.fasterxml.jackson.databind.JsonNode;

public record RoundOutput(
        int roundNumber,
        String rawResponse,
        JsonNode parsedJson,
        int promptTokens,
        int completionTokens,
        boolean success,
        String errorMessage
) {}
