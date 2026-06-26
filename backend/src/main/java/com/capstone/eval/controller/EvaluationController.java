package com.capstone.eval.controller;

import com.capstone.eval.dto.EvaluationResultResponse;
import com.capstone.eval.dto.EvaluationRoundResponse;
import com.capstone.eval.evaluation.hybrid.model.DecisionTrace;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.config.LlmProviderConfig;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.EvaluationRound;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.EvaluationVisibility;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.EvaluationRoundRepository;
import com.capstone.eval.service.BatchEvaluationService;
import com.capstone.eval.service.EvaluationOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationOrchestrator evaluationOrchestrator;
    private final EvaluationResultRepository evaluationResultRepository;
    private final EvaluationRoundRepository evaluationRoundRepository;
    private final BatchEvaluationService batchEvaluationService;
    private final LlmProviderConfig llmProviderConfig;
    private final ObjectMapper objectMapper;

    @PostMapping("/run/{submissionId}")
    public ResponseEntity<EvaluationResultResponse> evaluate(
            @PathVariable Long submissionId,
            @RequestParam(value = "method", defaultValue = "RULE_BASED") String method,
            @RequestParam(value = "visibility", defaultValue = "PUBLIC") String visibility) {

        EvaluationMethod evaluationMethod;
        try {
            evaluationMethod = EvaluationMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EvaluationException("Invalid evaluation method: '" + method
                    + "'. Supported values: RULE_BASED, LLM, HYBRID");
        }

        EvaluationVisibility evalVisibility;
        try {
            evalVisibility = EvaluationVisibility.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            evalVisibility = EvaluationVisibility.PUBLIC;
        }

        if (evaluationMethod == EvaluationMethod.LLM) {
            int max = llmProviderConfig.getMaxLlmRunsPerSubmission();
            long count = evaluationResultRepository.countBySubmissionIdAndMethod(submissionId, EvaluationMethod.LLM);
            if (count >= max) {
                throw new EvaluationException(
                        "LLM evaluation limit reached (" + max + " runs per submission).");
            }
        }

        EvaluationResult result = evaluationOrchestrator.evaluateSubmission(
                submissionId, evaluationMethod);
        result.setVisibility(evalVisibility);
        evaluationResultRepository.save(result);

        EvaluationResultResponse response = EvaluationResultResponse.fromEntity(result, objectMapper);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvaluationResultResponse> getEvaluation(@PathVariable Long id) {
        EvaluationResult result = evaluationResultRepository.findById(id)
                .orElseThrow(() -> new EvaluationException(
                        "Evaluation result not found with id: " + id));

        EvaluationResultResponse response = EvaluationResultResponse.fromEntity(result, objectMapper);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-submission/{submissionId}")
    public ResponseEntity<List<EvaluationResultResponse>> getBySubmission(
            @PathVariable Long submissionId) {

        List<EvaluationResultResponse> responses = evaluationResultRepository
                .findBySubmissionId(submissionId)
                .stream()
                .map(er -> EvaluationResultResponse.fromEntity(er, objectMapper))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/batch/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<Map<String, Object>> startBatch(
            @PathVariable Long taskId,
            @RequestParam(value = "method", defaultValue = "LLM") String method) {
        EvaluationMethod evaluationMethod;
        try {
            evaluationMethod = EvaluationMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EvaluationException("Invalid method: " + method);
        }
        batchEvaluationService.startBatchEvaluation(taskId, evaluationMethod);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Batch evaluation started");
        resp.put("taskId", taskId);
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/batch/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<?> batchStatus(@PathVariable Long taskId) {
        var status = batchEvaluationService.getStatus(taskId);
        if (status == null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "NOT_STARTED");
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{id}/rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<EvaluationRoundResponse>> getEvaluationRounds(@PathVariable Long id) {
        List<EvaluationRoundResponse> rounds = evaluationRoundRepository
                .findByEvaluationResultIdOrderByRoundNumberAsc(id)
                .stream()
                .map(EvaluationRoundResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(rounds);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<EvaluationResultResponse> verify(@PathVariable Long id) {
        EvaluationResult verifiedResult = evaluationOrchestrator.verifyEvaluation(id);
        EvaluationResultResponse response = EvaluationResultResponse.fromEntity(verifiedResult, objectMapper);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/decision-trace")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<DecisionTrace>> getDecisionTrace(@PathVariable Long id) {
        EvaluationResult result = evaluationResultRepository.findById(id)
                .orElseThrow(() -> new EvaluationException(
                        "Evaluation result not found with id: " + id));

        if (result.getMethod() != EvaluationMethod.HYBRID) {
            throw new EvaluationException("Decision trace is only available for HYBRID evaluations");
        }

        List<DecisionTrace> traces = evaluationRoundRepository
                .findByEvaluationResultIdOrderByRoundNumberAsc(id)
                .stream()
                .filter(r -> "HYBRID_EVIDENCE".equals(r.getRoundType()))
                .map(round -> {
                    try {
                        return objectMapper.readValue(round.getSystemPrompt(), DecisionTrace.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(traces);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("llmEnabled", llmProviderConfig.isLlmEnabled());
        status.put("llmProvider", llmProviderConfig.getDefaultProvider());
        status.put("maxLlmRunsPerSubmission", llmProviderConfig.getMaxLlmRunsPerSubmission());
        return ResponseEntity.ok(status);
    }
}
