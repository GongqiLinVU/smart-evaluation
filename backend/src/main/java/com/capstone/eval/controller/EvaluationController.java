package com.capstone.eval.controller;

import com.capstone.eval.dto.EvaluationResultResponse;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.config.LlmProviderConfig;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.service.EvaluationOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final LlmProviderConfig llmProviderConfig;
    private final ObjectMapper objectMapper;

    @PostMapping("/run/{submissionId}")
    public ResponseEntity<EvaluationResultResponse> evaluate(
            @PathVariable Long submissionId,
            @RequestParam(value = "method", defaultValue = "RULE_BASED") String method) {

        EvaluationMethod evaluationMethod;
        try {
            evaluationMethod = EvaluationMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EvaluationException("Invalid evaluation method: '" + method
                    + "'. Supported values: RULE_BASED, LLM");
        }

        EvaluationResult result = evaluationOrchestrator.evaluateSubmission(
                submissionId, evaluationMethod);
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("llmEnabled", llmProviderConfig.isLlmEnabled());
        status.put("llmProvider", llmProviderConfig.getDefaultProvider());
        return ResponseEntity.ok(status);
    }
}
