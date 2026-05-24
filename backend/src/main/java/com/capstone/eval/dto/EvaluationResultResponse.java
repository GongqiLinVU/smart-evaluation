package com.capstone.eval.dto;

import com.capstone.eval.model.EvaluationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public record EvaluationResultResponse(
        Long id,
        Long submissionId,
        String method,
        Integer overallScore,
        String overallLevel,
        String overallFeedback,
        List<String> strengths,
        List<String> improvements,
        Double confidence,
        String rawLlmResponse,
        LocalDateTime evaluatedAt,
        List<CriterionScoreResponse> criteria
) {

    private static final Logger log = LoggerFactory.getLogger(EvaluationResultResponse.class);

    public static EvaluationResultResponse fromEntity(EvaluationResult result, ObjectMapper mapper) {
        List<String> strengthsList = deserializeStringList(result.getStrengths(), mapper);
        List<String> improvementsList = deserializeStringList(result.getImprovements(), mapper);

        List<CriterionScoreResponse> criteriaList = result.getCriterionScores() != null
                ? result.getCriterionScores().stream()
                        .map(cs -> CriterionScoreResponse.fromEntity(cs, mapper))
                        .collect(Collectors.toList())
                : Collections.emptyList();

        return new EvaluationResultResponse(
                result.getId(),
                result.getSubmission() != null ? result.getSubmission().getId() : null,
                result.getMethod() != null ? result.getMethod().name() : null,
                result.getOverallScore(),
                result.getOverallLevel() != null ? result.getOverallLevel().name() : null,
                result.getOverallFeedback(),
                strengthsList,
                improvementsList,
                result.getConfidence(),
                result.getRawLlmResponse(),
                result.getEvaluatedAt(),
                criteriaList
        );
    }

    private static List<String> deserializeStringList(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
