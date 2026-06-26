package com.capstone.eval.dto;

import com.capstone.eval.model.EvaluationRound;

import java.time.LocalDateTime;

public record EvaluationRoundResponse(
        Long id,
        Integer roundNumber,
        String roundType,
        String inputSections,
        String targetCriteria,
        String systemPrompt,
        String userPrompt,
        String rawResponse,
        Integer promptTokens,
        Integer completionTokens,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static EvaluationRoundResponse fromEntity(EvaluationRound round) {
        return new EvaluationRoundResponse(
                round.getId(),
                round.getRoundNumber(),
                round.getRoundType(),
                round.getInputSections(),
                round.getTargetCriteria(),
                round.getSystemPrompt(),
                round.getUserPrompt(),
                round.getRawResponse(),
                round.getPromptTokens(),
                round.getCompletionTokens(),
                round.getStatus(),
                round.getStartedAt(),
                round.getCompletedAt()
        );
    }
}
