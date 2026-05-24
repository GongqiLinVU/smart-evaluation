package com.capstone.eval.service;

import com.capstone.eval.dto.LlmConfigRequest;
import com.capstone.eval.dto.PromptPreviewResponse;
import com.capstone.eval.evaluation.llm.multiround.DynamicPromptBuilder;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.repository.LlmConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final LlmConfigRepository llmConfigRepository;
    private final RulePackageService rulePackageService;
    private final DynamicPromptBuilder dynamicPromptBuilder;

    @Transactional(readOnly = true)
    public List<LlmConfig> getAll() {
        return llmConfigRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public LlmConfig getById(Long id) {
        return llmConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("LLM config not found: " + id));
    }

    @Transactional(readOnly = true)
    public LlmConfig getDefault() {
        return llmConfigRepository.findByIsDefaultTrue().orElse(null);
    }

    @Transactional
    public LlmConfig create(LlmConfigRequest request) {
        LlmConfig config = LlmConfig.builder()
                .name(request.name())
                .systemPromptTemplate(request.systemPromptTemplate())
                .additionalContext(request.additionalContext())
                .outputFormatTemplate(request.outputFormatTemplate())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .provider(request.provider())
                .model(request.model())
                .isDefault(Boolean.TRUE.equals(request.isDefault()))
                .build();

        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaultFlag();
        }

        return llmConfigRepository.save(config);
    }

    @Transactional
    public LlmConfig update(Long id, LlmConfigRequest request) {
        LlmConfig config = getById(id);
        config.setName(request.name());
        config.setSystemPromptTemplate(request.systemPromptTemplate());
        config.setAdditionalContext(request.additionalContext());
        config.setOutputFormatTemplate(request.outputFormatTemplate());
        config.setTemperature(request.temperature());
        config.setMaxTokens(request.maxTokens());
        config.setProvider(request.provider());
        config.setModel(request.model());

        if (Boolean.TRUE.equals(request.isDefault()) && !Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefaultFlag();
            config.setIsDefault(true);
        } else if (Boolean.FALSE.equals(request.isDefault())) {
            config.setIsDefault(false);
        }

        return llmConfigRepository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        llmConfigRepository.deleteById(id);
    }

    @Transactional
    public LlmConfig duplicate(Long id) {
        LlmConfig source = getById(id);
        LlmConfig copy = LlmConfig.builder()
                .name(source.getName() + " (Copy)")
                .systemPromptTemplate(source.getSystemPromptTemplate())
                .additionalContext(source.getAdditionalContext())
                .outputFormatTemplate(source.getOutputFormatTemplate())
                .temperature(source.getTemperature())
                .maxTokens(source.getMaxTokens())
                .provider(source.getProvider())
                .model(source.getModel())
                .isDefault(false)
                .build();
        return llmConfigRepository.save(copy);
    }

    @Transactional(readOnly = true)
    public PromptPreviewResponse previewPrompt(Long configId, Long rulePackageId) {
        LlmConfig config = getById(configId);
        RulePackage rulePackage = rulePackageService.getById(rulePackageId);

        List<RulePackageItem> enabledRules = rulePackage.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .toList();

        String systemPrompt = dynamicPromptBuilder.previewSystemPrompt(config, enabledRules);

        List<String> criteriaIncluded = enabledRules.stream()
                .map(item -> item.getRule().getName())
                .toList();

        int estimatedTokens = (int) (systemPrompt.length() / 3.5);

        return new PromptPreviewResponse(systemPrompt, estimatedTokens, criteriaIncluded);
    }

    private void clearDefaultFlag() {
        llmConfigRepository.findByIsDefaultTrue().ifPresent(existing -> {
            existing.setIsDefault(false);
            llmConfigRepository.save(existing);
        });
    }
}
