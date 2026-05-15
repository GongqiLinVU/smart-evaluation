package com.capstone.eval.evaluation.llm;

import com.capstone.eval.exception.EvaluationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LLM provider implementation for the DeepSeek API (OpenAI-compatible format).
 */
@Component
@Slf4j
public class DeepSeekProvider implements LlmProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeepSeekProvider(
            @Value("${eval.llm.deepseek.api-key:}") String apiKey,
            @Value("${eval.llm.deepseek.base-url:https://api.deepseek.com/v1}") String baseUrl,
            @Value("${eval.llm.deepseek.model:deepseek-chat}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public String getName() {
        return "deepseek";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, LlmOptions options) {
        if (!isConfigured()) {
            throw new EvaluationException("DeepSeek provider is not configured: API key is missing");
        }

        String effectiveModel = options.model() != null ? options.model() : this.model;

        List<Map<String, String>> messageList = messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();

        Map<String, Object> requestBody = Map.of(
                "model", effectiveModel,
                "messages", messageList,
                "temperature", options.temperature(),
                "max_tokens", options.maxTokens()
        );

        log.info("Calling DeepSeek API: model={}, messages={}, temperature={}, max_tokens={}",
                effectiveModel, messages.size(), options.temperature(), options.maxTokens());

        try {
            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            throw new EvaluationException("DeepSeek API call failed: " + e.getMessage(), e);
        }
    }

    private LlmResponse parseResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            String content = root.path("choices").path(0)
                    .path("message").path("content").asText("");

            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);

            log.info("DeepSeek response received: prompt_tokens={}, completion_tokens={}",
                    promptTokens, completionTokens);

            return new LlmResponse(content, promptTokens, completionTokens);
        } catch (Exception e) {
            log.error("Failed to parse DeepSeek response: {}", responseJson, e);
            throw new EvaluationException("Failed to parse DeepSeek API response", e);
        }
    }
}
