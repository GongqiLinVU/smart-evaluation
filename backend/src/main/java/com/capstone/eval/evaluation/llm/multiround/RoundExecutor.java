package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.evaluation.llm.*;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.parser.ParsedDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoundExecutor {

    private final LlmProviderFactory providerFactory;
    private final DynamicPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public RoundOutput executeEvaluationRound(
            RoundSpec roundSpec,
            ParsedDocument document,
            LlmConfig config,
            List<RulePackageItem> enabledRules,
            String systemPrompt
    ) {
        log.info("Executing round {} ({}) with {} sections",
                roundSpec.roundNumber(), roundSpec.roundType(), roundSpec.sectionIndices().size());

        try {
            String userPrompt = promptBuilder.buildRoundUserPrompt(document, roundSpec);

            LlmProvider provider = resolveProvider(config);
            LlmOptions options = resolveOptions(config);

            List<LlmMessage> messages = List.of(
                    LlmMessage.system(systemPrompt),
                    LlmMessage.user(userPrompt)
            );

            LlmResponse response = provider.chat(messages, options);

            JsonNode parsed = tryParseJson(response.content());

            return new RoundOutput(
                    roundSpec.roundNumber(),
                    response.content(),
                    parsed,
                    response.promptTokens(),
                    response.completionTokens(),
                    parsed != null,
                    parsed == null ? "Failed to parse LLM response as JSON" : null
            );
        } catch (Exception e) {
            log.error("Round {} failed: {}", roundSpec.roundNumber(), e.getMessage(), e);
            return new RoundOutput(
                    roundSpec.roundNumber(),
                    null,
                    null,
                    0, 0,
                    false,
                    e.getMessage()
            );
        }
    }

    public RoundOutput executeSynthesisRound(
            List<RoundOutput> priorOutputs,
            LlmConfig config,
            List<RulePackageItem> enabledRules,
            String systemPrompt
    ) {
        log.info("Executing synthesis round from {} prior rounds", priorOutputs.size());

        try {
            String synthesisPrompt = promptBuilder.buildSynthesisPrompt(priorOutputs, enabledRules);

            LlmProvider provider = resolveProvider(config);
            LlmOptions options = resolveOptions(config);

            List<LlmMessage> messages = List.of(
                    LlmMessage.system(systemPrompt),
                    LlmMessage.user(synthesisPrompt)
            );

            LlmResponse response = provider.chat(messages, options);

            JsonNode parsed = tryParseJson(response.content());

            int roundNumber = priorOutputs.stream()
                    .mapToInt(RoundOutput::roundNumber).max().orElse(0) + 1;

            return new RoundOutput(
                    roundNumber,
                    response.content(),
                    parsed,
                    response.promptTokens(),
                    response.completionTokens(),
                    parsed != null,
                    parsed == null ? "Failed to parse synthesis response as JSON" : null
            );
        } catch (Exception e) {
            log.error("Synthesis round failed: {}", e.getMessage(), e);
            int roundNumber = priorOutputs.stream()
                    .mapToInt(RoundOutput::roundNumber).max().orElse(0) + 1;
            return new RoundOutput(roundNumber, null, null, 0, 0, false, e.getMessage());
        }
    }

    private LlmProvider resolveProvider(LlmConfig config) {
        if (config != null && config.getProvider() != null && !config.getProvider().isBlank()) {
            return providerFactory.getProvider(config.getProvider());
        }
        return providerFactory.getDefaultProvider();
    }

    private LlmOptions resolveOptions(LlmConfig config) {
        double temperature = (config != null && config.getTemperature() != null)
                ? config.getTemperature() : 0.1;
        int maxTokens = (config != null && config.getMaxTokens() != null)
                ? config.getMaxTokens() : 4096;
        String model = (config != null && config.getModel() != null)
                ? config.getModel() : null;
        return new LlmOptions(temperature, maxTokens, model);
    }

    private JsonNode tryParseJson(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            String json = extractJson(content);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
