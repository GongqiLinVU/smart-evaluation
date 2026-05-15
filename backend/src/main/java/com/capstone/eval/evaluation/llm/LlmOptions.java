package com.capstone.eval.evaluation.llm;

/**
 * Options for an LLM chat request.
 *
 * @param temperature sampling temperature (0.0 = deterministic, 1.0 = creative)
 * @param maxTokens   maximum number of tokens to generate
 * @param model       the model identifier to use (provider-specific)
 */
public record LlmOptions(double temperature, int maxTokens, String model) {

    /**
     * Sensible defaults for capstone evaluation: low temperature for consistent scoring,
     * generous token budget for detailed feedback.
     */
    public static LlmOptions defaults(String model) {
        return new LlmOptions(0.1, 4096, model);
    }
}
