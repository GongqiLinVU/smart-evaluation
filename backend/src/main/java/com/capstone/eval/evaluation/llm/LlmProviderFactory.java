package com.capstone.eval.evaluation.llm;

import com.capstone.eval.exception.EvaluationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory that holds all registered {@link LlmProvider} implementations and resolves
 * the appropriate provider by name.
 */
@Component
@Slf4j
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providers;

    @Value("${eval.llm.provider:deepseek}")
    private String defaultProvider;

    public LlmProviderFactory(List<LlmProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(LlmProvider::getName, Function.identity()));
        log.info("Registered LLM providers: {}", providers.keySet());
    }

    /**
     * Get a provider by name.
     *
     * @param name the provider name (e.g. "deepseek", "openai")
     * @return the matching provider
     * @throws EvaluationException if no provider with the given name is registered
     */
    public LlmProvider getProvider(String name) {
        LlmProvider provider = providers.get(name);
        if (provider == null) {
            throw new EvaluationException(
                    "Unknown LLM provider: '" + name + "'. Available: " + providers.keySet());
        }
        return provider;
    }

    /**
     * @return the provider configured as the default via {@code eval.llm.provider}
     */
    public LlmProvider getDefaultProvider() {
        return getProvider(defaultProvider);
    }

    /**
     * @return true if at least one registered provider has a valid API key configured
     */
    public boolean isAnyProviderConfigured() {
        return providers.values().stream().anyMatch(LlmProvider::isConfigured);
    }
}
