package com.capstone.eval.evaluation.llm;

import com.capstone.eval.evaluation.EvaluationEngine;
import com.capstone.eval.evaluation.llm.multiround.*;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.*;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.parser.ParsedDocument;
import com.capstone.eval.repository.LlmConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmEvaluationEngine implements EvaluationEngine {

    private final LlmProviderFactory providerFactory;
    private final PromptBuilder promptBuilder;
    private final LlmResponseParser responseParser;
    private final EvaluationRoundPlanner roundPlanner;
    private final DynamicPromptBuilder dynamicPromptBuilder;
    private final RoundExecutor roundExecutor;
    private final SynthesisAggregator synthesisAggregator;
    private final LlmConfigRepository llmConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    public EvaluationResult evaluate(ParsedDocument document, Submission submission) {
        return evaluate(document, submission, null, null);
    }

    public EvaluationResult evaluate(ParsedDocument document, Submission submission, RulePackage rulePackage) {
        return evaluate(document, submission, rulePackage, null);
    }

    public EvaluationResult evaluate(ParsedDocument document, Submission submission, RulePackage rulePackage, LlmConfig resolvedConfig) {
        log.info("Starting LLM evaluation for submission id={}", submission.getId());

        LlmProvider provider = providerFactory.getDefaultProvider();
        if (!provider.isConfigured()) {
            throw new EvaluationException(
                    "LLM provider '" + provider.getName() + "' is not configured.");
        }

        LlmConfig config = resolvedConfig != null
                ? resolvedConfig
                : llmConfigRepository.findByIsDefaultTrue().orElse(null);

        List<RulePackageItem> enabledRules = resolveEnabledRules(rulePackage);

        if (enabledRules.isEmpty()) {
            log.warn("No enabled rules found, falling back to legacy single-pass");
            return legacyEvaluate(document, submission);
        }

        EvaluationPlan plan = roundPlanner.planRounds(document, enabledRules);
        log.info("Evaluation plan: strategy={}, rounds={}",
                plan.strategy(), plan.evaluationRounds().size());

        String systemPrompt = dynamicPromptBuilder.buildSystemPrompt(config, enabledRules, rulePackage);

        EvaluationResult result;
        if (plan.strategy() == PlanStrategy.SINGLE_PASS) {
            result = executeSinglePass(plan, document, submission, config, enabledRules, systemPrompt, rulePackage);
        } else {
            result = executeMultiPass(plan, document, submission, config, enabledRules, systemPrompt, rulePackage);
        }

        result.setMethod(EvaluationMethod.LLM);

        log.info("LLM evaluation complete for submission id={}: overall={} ({})",
                submission.getId(), result.getOverallScore(), result.getOverallLevel());

        return result;
    }

    private EvaluationResult executeSinglePass(
            EvaluationPlan plan,
            ParsedDocument document,
            Submission submission,
            LlmConfig config,
            List<RulePackageItem> enabledRules,
            String systemPrompt,
            RulePackage rulePackage
    ) {
        RoundSpec roundSpec = plan.evaluationRounds().get(0);
        LocalDateTime startedAt = LocalDateTime.now();

        RoundOutput output = roundExecutor.executeEvaluationRound(
                roundSpec, document, config, enabledRules, systemPrompt);

        EvaluationResult result = synthesisAggregator.synthesizeSinglePass(
                output, submission, enabledRules, rulePackage);

        EvaluationRound round = buildRoundRecord(output, roundSpec, startedAt, systemPrompt, output.userPrompt());
        round.setEvaluationResult(result);
        result.getRounds().add(round);

        return result;
    }

    private EvaluationResult executeMultiPass(
            EvaluationPlan plan,
            ParsedDocument document,
            Submission submission,
            LlmConfig config,
            List<RulePackageItem> enabledRules,
            String systemPrompt,
            RulePackage rulePackage
    ) {
        List<LocalDateTime> startTimes = new ArrayList<>();
        List<CompletableFuture<RoundOutput>> futures = new ArrayList<>();

        for (RoundSpec roundSpec : plan.evaluationRounds()) {
            startTimes.add(LocalDateTime.now());
            futures.add(CompletableFuture.supplyAsync(() ->
                    roundExecutor.executeEvaluationRound(
                            roundSpec, document, config, enabledRules, systemPrompt)));
        }

        List<RoundOutput> roundOutputs = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        List<RoundOutput> successfulOutputs = roundOutputs.stream()
                .filter(RoundOutput::success)
                .toList();

        if (successfulOutputs.isEmpty()) {
            throw new EvaluationException("All evaluation rounds failed");
        }

        LocalDateTime synthesisStart = LocalDateTime.now();
        RoundOutput synthesisOutput = null;
        if (plan.synthesisRound() != null && successfulOutputs.size() > 1) {
            synthesisOutput = roundExecutor.executeSynthesisRound(
                    successfulOutputs, config, enabledRules, systemPrompt);
        }

        EvaluationResult result = synthesisAggregator.synthesize(
                roundOutputs, synthesisOutput, submission, enabledRules, plan, rulePackage);

        for (int i = 0; i < roundOutputs.size(); i++) {
            RoundSpec spec = plan.evaluationRounds().get(i);
            RoundOutput ro = roundOutputs.get(i);
            EvaluationRound round = buildRoundRecord(ro, spec, startTimes.get(i), systemPrompt, ro.userPrompt());
            round.setEvaluationResult(result);
            result.getRounds().add(round);
        }

        if (synthesisOutput != null) {
            EvaluationRound synthRound = buildRoundRecord(
                    synthesisOutput, plan.synthesisRound(), synthesisStart, systemPrompt, synthesisOutput.userPrompt());
            synthRound.setEvaluationResult(result);
            result.getRounds().add(synthRound);
        }

        return result;
    }

    private EvaluationResult legacyEvaluate(ParsedDocument document, Submission submission) {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(document);

        LlmProvider provider = providerFactory.getDefaultProvider();
        LlmOptions options = LlmOptions.defaults(null);

        List<LlmMessage> messages = List.of(
                LlmMessage.system(systemPrompt),
                LlmMessage.user(userPrompt)
        );

        LlmResponse llmResponse = provider.chat(messages, options);
        EvaluationResult result = responseParser.parse(llmResponse.content(), submission);
        result.setMethod(EvaluationMethod.LLM);
        return result;
    }

    private List<RulePackageItem> resolveEnabledRules(RulePackage rulePackage) {
        if (rulePackage == null || rulePackage.getItems() == null || rulePackage.getItems().isEmpty()) {
            return List.of();
        }
        return rulePackage.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .toList();
    }

    private EvaluationRound buildRoundRecord(RoundOutput output, RoundSpec spec, LocalDateTime startedAt, String systemPrompt, String userPrompt) {
        String inputSections = null;
        String targetCriteria = null;
        try {
            inputSections = objectMapper.writeValueAsString(spec.sectionIndices());
            targetCriteria = objectMapper.writeValueAsString(spec.criteriaKeys());
        } catch (Exception e) {
            log.warn("Failed to serialize round spec: {}", e.getMessage());
        }

        return EvaluationRound.builder()
                .roundNumber(spec.roundNumber())
                .roundType(spec.roundType())
                .inputSections(inputSections)
                .targetCriteria(targetCriteria)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .rawResponse(output.rawResponse())
                .promptTokens(output.promptTokens())
                .completionTokens(output.completionTokens())
                .status(output.success() ? "SUCCESS" : "FAILED")
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build();
    }
}
