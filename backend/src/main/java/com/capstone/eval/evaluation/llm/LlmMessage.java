package com.capstone.eval.evaluation.llm;

/**
 * A single message in an LLM conversation.
 *
 * @param role    the message role, e.g. "system", "user", "assistant"
 * @param content the text content of the message
 */
public record LlmMessage(String role, String content) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
