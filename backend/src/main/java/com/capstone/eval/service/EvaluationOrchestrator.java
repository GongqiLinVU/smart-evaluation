package com.capstone.eval.service;

import com.capstone.eval.config.LlmProviderConfig;
import com.capstone.eval.evaluation.EvaluationEngine;
import com.capstone.eval.evaluation.llm.LlmEvaluationEngine;
import com.capstone.eval.evaluation.rule.RuleBasedEngine;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.ParsedDocumentEntity;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.EvaluationStatus;
import com.capstone.eval.parser.ParsedDocument;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.ParsedDocumentRepository;
import com.capstone.eval.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationOrchestrator {

    private final SubmissionRepository submissionRepository;
    private final DocumentParserService documentParserService;
    private final RuleBasedEngine ruleBasedEngine;
    private final LlmEvaluationEngine llmEvaluationEngine;
    private final EvaluationResultRepository evaluationResultRepository;
    private final ParsedDocumentRepository parsedDocumentRepository;
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

            // 4. Pick the evaluation engine based on method
            EvaluationEngine engine;
            if (method == EvaluationMethod.RULE_BASED) {
                engine = ruleBasedEngine;
            } else if (method == EvaluationMethod.LLM) {
                if (!llmProviderConfig.isLlmEnabled()) {
                    throw new EvaluationException(
                            "LLM evaluation is not enabled. Set eval.llm.enabled=true in configuration.");
                }
                engine = llmEvaluationEngine;
            } else {
                throw new EvaluationException("Unsupported evaluation method: " + method);
            }

            // 5. Run evaluation
            EvaluationResult result = engine.evaluate(parsedDoc, submission);

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
}
