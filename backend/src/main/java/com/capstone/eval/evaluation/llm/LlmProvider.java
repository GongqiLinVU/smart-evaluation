package com.capstone.eval.evaluation.llm;

import java.util.List;

/**
 * Abstraction over different LLM API providers (DeepSeek, OpenAI, Anthropic, etc.).
 * Each implementation handles the HTTP call and response parsing for its vendor.
 */
public interface LlmProvider {

    /**
     * @return the provider name used to look up the implementation, e.g. "deepseek", "openai"
     */
    String getName();

    /**
     * @return true if the provider has a valid API key configured
     */
    boolean isConfigured();

    /**
     * Send a chat completion request to the LLM.
     *
     * @param messages the conversation messages (system + user)
     * @param options  request parameters (temperature, max tokens, model)
     * @return the LLM response with content and token usage
     */
    LlmResponse chat(List<LlmMessage> messages, LlmOptions options);
}
