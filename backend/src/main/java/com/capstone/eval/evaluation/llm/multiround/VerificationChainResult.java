package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.EvaluationRound;

import java.util.List;

public record VerificationChainResult(
        EvaluationResult result,
        List<EvaluationRound> rounds,
        int totalPromptTokens,
        int totalCompletionTokens,
        int roundsExecuted,
        boolean changed
) {
    public static VerificationChainResult unchanged(
            EvaluationResult original,
            List<EvaluationRound> rounds,
            int promptTokens,
            int completionTokens
    ) {
        return new VerificationChainResult(original, rounds, promptTokens, completionTokens, rounds.size(), false);
    }
}
