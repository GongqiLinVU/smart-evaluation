package com.capstone.eval.evaluation.llm;

/**
 * The response from an LLM chat completion call.
 *
 * @param content          the generated text content
 * @param promptTokens     number of tokens consumed by the prompt
 * @param completionTokens number of tokens generated in the response
 */
public record LlmResponse(String content, int promptTokens, int completionTokens) {
}
