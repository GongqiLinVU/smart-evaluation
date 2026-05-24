package com.capstone.eval.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for LLM provider settings.
 * Exposes the top-level {@code eval.llm.enabled} flag so services can check
 * whether LLM evaluation is enabled without injecting @Value directly.
 */
@Configuration
@Getter
@Slf4j
public class LlmProviderConfig {

    @Value("${eval.llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${eval.llm.provider:deepseek}")
    private String defaultProvider;

    @Value("${eval.llm.max-runs-per-submission:3}")
    private int maxLlmRunsPerSubmission;

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("LLM evaluation enabled={}, default provider={}", llmEnabled, defaultProvider);
    }
}
