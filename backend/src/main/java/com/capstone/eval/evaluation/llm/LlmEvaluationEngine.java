package com.capstone.eval.evaluation.llm;

import com.capstone.eval.evaluation.EvaluationEngine;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.parser.ParsedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM-based evaluation engine that sends the parsed document to a large language model
 * for assessment. Implements the same {@link EvaluationEngine} interface as the rule-based
 * engine, allowing them to be used interchangeably.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmEvaluationEngine implements EvaluationEngine {

    private final LlmProviderFactory providerFactory;
    private final PromptBuilder promptBuilder;
    private final LlmResponseParser responseParser;

    @Override
    public EvaluationResult evaluate(ParsedDocument document, Submission submission) {
        log.info("Starting LLM-based evaluation for submission id={}", submission.getId());

        // 1. Get the default provider and verify it is configured
        LlmProvider provider = providerFactory.getDefaultProvider();
        if (!provider.isConfigured()) {
            throw new EvaluationException(
                    "LLM provider '" + provider.getName() + "' is not configured. "
                            + "Please set the API key in application configuration.");
        }

        // 2. Build the prompts
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(document);

        List<LlmMessage> messages = List.of(
                LlmMessage.system(systemPrompt),
                LlmMessage.user(userPrompt)
        );

        // 3. Call the LLM
        LlmOptions options = LlmOptions.defaults(null); // use provider's default model
        log.info("Sending evaluation request to provider '{}' (system prompt: {} chars, user prompt: {} chars)",
                provider.getName(), systemPrompt.length(), userPrompt.length());

        LlmResponse llmResponse;
        try {
            llmResponse = provider.chat(messages, options);
        } catch (Exception e) {
            log.error("LLM API call failed for submission id={}", submission.getId(), e);
            throw new EvaluationException("LLM evaluation failed: " + e.getMessage(), e);
        }

        log.info("LLM response received for submission id={}: {} chars, prompt_tokens={}, completion_tokens={}",
                submission.getId(), llmResponse.content().length(),
                llmResponse.promptTokens(), llmResponse.completionTokens());

        // 4. Parse the LLM response into an EvaluationResult
        EvaluationResult result = responseParser.parse(llmResponse.content(), submission);

        // 5. Ensure method is set to LLM
        result.setMethod(EvaluationMethod.LLM);

        log.info("LLM evaluation complete for submission id={}: overall={}/30 ({})",
                submission.getId(), result.getOverallScore(), result.getOverallLevel());

        return result;
    }
}
