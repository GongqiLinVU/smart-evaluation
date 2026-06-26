package com.capstone.eval.service;

import com.capstone.eval.config.LlmProviderConfig;
import com.capstone.eval.evaluation.EvaluationEngine;
import com.capstone.eval.evaluation.hybrid.HybridEvaluationEngine;
import com.capstone.eval.evaluation.llm.LlmEvaluationEngine;
import com.capstone.eval.evaluation.llm.multiround.VerificationChainExecutor;
import com.capstone.eval.evaluation.llm.multiround.VerificationChainResult;
import com.capstone.eval.evaluation.rule.RuleBasedEngine;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.*;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.EvaluationStatus;
import com.capstone.eval.model.enums.EvaluationVisibility;
import com.capstone.eval.parser.ParsedDocument;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.LlmConfigRepository;
import com.capstone.eval.repository.ParsedDocumentRepository;
import com.capstone.eval.repository.RulePackageRepository;
import com.capstone.eval.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationOrchestrator {

    private final SubmissionRepository submissionRepository;
    private final DocumentParserService documentParserService;
    private final RuleBasedEngine ruleBasedEngine;
    private final LlmEvaluationEngine llmEvaluationEngine;
    private final HybridEvaluationEngine hybridEvaluationEngine;
    private final VerificationChainExecutor verificationChainExecutor;
    private final EvaluationResultRepository evaluationResultRepository;
    private final ParsedDocumentRepository parsedDocumentRepository;
    private final RulePackageRepository rulePackageRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final LlmProviderConfig llmProviderConfig;
    private final ObjectMapper objectMapper;

    @Transactional
    public EvaluationResult evaluateSubmission(Long submissionId, EvaluationMethod method) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new EvaluationException(
                        "Submission not found with id: " + submissionId));

        try {
            // 1. Update status to PARSING
            submission.setStatus(EvaluationStatus.PARSING);
            submissionRepository.save(submission);

            // 2. Check if already parsed; if not, parse and store
            ParsedDocument parsedDoc;
            Optional<ParsedDocumentEntity> existingEntity =
                    parsedDocumentRepository.findBySubmissionId(submissionId);

            if (existingEntity.isPresent()) {
                log.info("Document already parsed for submission id={}, loading from database",
                        submissionId);
                parsedDoc = objectMapper.readValue(
                        existingEntity.get().getDocumentStructure(), ParsedDocument.class);
            } else {
                log.info("Parsing document for submission id={}", submissionId);
                parsedDoc = documentParserService.parseAndStore(submission);
            }

            // 3. Update status to EVALUATING
            submission.setStatus(EvaluationStatus.EVALUATING);
            submissionRepository.save(submission);

            // 4. Run evaluation
            EvaluationResult result;
            if (method == EvaluationMethod.RULE_BASED) {
                RulePackage rulePackage = resolveRulePackage(submission);
                result = ruleBasedEngine.evaluate(parsedDoc, submission, rulePackage);
            } else if (method == EvaluationMethod.LLM) {
                if (!llmProviderConfig.isLlmEnabled()) {
                    throw new EvaluationException(
                            "LLM evaluation is not enabled. Set eval.llm.enabled=true in configuration.");
                }
                RulePackage llmRulePackage = resolveRulePackage(submission);
                LlmConfig llmConfig = resolveLlmConfig(submission);
                result = llmEvaluationEngine.evaluate(parsedDoc, submission, llmRulePackage, llmConfig);
            } else if (method == EvaluationMethod.HYBRID) {
                if (!llmProviderConfig.isLlmEnabled()) {
                    throw new EvaluationException(
                            "LLM evaluation is not enabled. Set eval.llm.enabled=true in configuration.");
                }
                RulePackage hybridRulePackage = resolveRulePackage(submission);
                LlmConfig llmConfig = resolveLlmConfig(submission);
                result = hybridEvaluationEngine.evaluate(parsedDoc, submission, hybridRulePackage, llmConfig);
            } else {
                throw new EvaluationException("Unsupported evaluation method: " + method);
            }

            // 6. Save result to database
            EvaluationResult savedResult = evaluationResultRepository.save(result);

            // 7. Update submission status to COMPLETED
            submission.setStatus(EvaluationStatus.COMPLETED);
            submissionRepository.save(submission);

            log.info("Evaluation completed for submission id={} using method={}",
                    submissionId, method);

            return savedResult;

        } catch (EvaluationException e) {
            // Mark as FAILED and rethrow
            submission.setStatus(EvaluationStatus.FAILED);
            submissionRepository.save(submission);
            throw e;
        } catch (Exception e) {
            // Mark as FAILED and wrap in EvaluationException
            submission.setStatus(EvaluationStatus.FAILED);
            submissionRepository.save(submission);
            throw new EvaluationException("Evaluation failed for submission id "
                    + submissionId + ": " + e.getMessage(), e);
        }
    }

    private RulePackage resolveRulePackage(Submission submission) {
        if (submission.getTask() != null && submission.getTask().getRulePackage() != null) {
            return submission.getTask().getRulePackage();
        }
        if (submission.getProject() != null && submission.getProject().getRulePackage() != null) {
            return submission.getProject().getRulePackage();
        }
        return rulePackageRepository.findByIsDefaultTrue().orElse(null);
    }

    private LlmConfig resolveLlmConfig(Submission submission) {
        if (submission.getTask() != null && submission.getTask().getLlmConfig() != null) {
            return submission.getTask().getLlmConfig();
        }
        return llmConfigRepository.findByIsDefaultTrue().orElse(null);
    }

    @Transactional
    public EvaluationResult verifyEvaluation(Long evaluationId) {
        EvaluationResult originalResult = evaluationResultRepository.findById(evaluationId)
                .orElseThrow(() -> new EvaluationException(
                        "Evaluation result not found with id: " + evaluationId));

        if (originalResult.getMethod() != EvaluationMethod.LLM) {
            throw new EvaluationException("Verification is only supported for LLM evaluations");
        }

        Submission submission = originalResult.getSubmission();
        RulePackage rulePackage = resolveRulePackage(submission);
        LlmConfig llmConfig = resolveLlmConfig(submission);

        List<RulePackageItem> enabledRules = resolveEnabledRules(rulePackage);
        if (enabledRules.isEmpty()) {
            throw new EvaluationException("No enabled rules found for verification");
        }

        // Load the parsed document so verification rounds have document evidence
        ParsedDocument parsedDocument = loadParsedDocument(submission.getId());

        log.info("Running verification chain for evaluation id={}, submission id={}, hasDocument={}",
                evaluationId, submission.getId(), parsedDocument != null);

        VerificationChainResult chainResult = verificationChainExecutor.execute(
                originalResult, submission, rulePackage, enabledRules, llmConfig, parsedDocument);

        EvaluationResult verifiedResult = chainResult.result();
        verifiedResult.setVisibility(EvaluationVisibility.INTERNAL);

        for (EvaluationRound round : chainResult.rounds()) {
            round.setEvaluationResult(verifiedResult);
            verifiedResult.getRounds().add(round);
        }

        EvaluationResult saved = evaluationResultRepository.save(verifiedResult);

        log.info("Verification complete: {} rounds executed, score {} -> {}, changed={}",
                chainResult.roundsExecuted(),
                originalResult.getOverallScore(),
                saved.getOverallScore(),
                chainResult.changed());

        return saved;
    }

    private ParsedDocument loadParsedDocument(Long submissionId) {
        try {
            Optional<ParsedDocumentEntity> entity =
                    parsedDocumentRepository.findBySubmissionId(submissionId);
            if (entity.isPresent()) {
                return objectMapper.readValue(
                        entity.get().getDocumentStructure(), ParsedDocument.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load parsed document for submission {}: {}",
                    submissionId, e.getMessage());
        }
        return null;
    }

    private List<RulePackageItem> resolveEnabledRules(RulePackage rulePackage) {
        if (rulePackage == null || rulePackage.getItems() == null || rulePackage.getItems().isEmpty()) {
            return List.of();
        }
        return rulePackage.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .toList();
    }
}
