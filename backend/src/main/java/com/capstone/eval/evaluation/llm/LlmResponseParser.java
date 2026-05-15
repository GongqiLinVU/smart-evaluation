package com.capstone.eval.evaluation.llm;

import com.capstone.eval.model.CriterionScore;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.PerformanceLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses the raw JSON response from an LLM into an {@link EvaluationResult} entity
 * with associated {@link CriterionScore} records.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Mapping from JSON criterion keys to display names used in CriterionScore.criterionName.
     * These match the names used by the rule-based engine for consistency.
     */
    private static final Map<String, String> CRITERION_KEY_TO_NAME = Map.of(
            "logic_explanation", "Logic Explanation",
            "methodology", "Methodology",
            "implementation_detail", "Implementation Detail"
    );

    /**
     * Parse the raw LLM response string into a fully populated EvaluationResult.
     *
     * @param llmResponse the raw text returned by the LLM (expected to contain JSON)
     * @param submission  the submission being evaluated
     * @return a populated EvaluationResult (not yet persisted)
     */
    public EvaluationResult parse(String llmResponse, Submission submission) {
        String json = extractJson(llmResponse);

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.LLM)
                .rawLlmResponse(llmResponse)
                .evaluatedAt(LocalDateTime.now())
                .criterionScores(new ArrayList<>())
                .build();

        try {
            JsonNode root = objectMapper.readTree(json);

            // Parse overall fields
            int overallScore = root.path("overall_score").asInt(18);
            overallScore = snapToValidScore(overallScore);
            result.setOverallScore(overallScore);

            String overallLevelStr = root.path("overall_level").asText("COMPETENT");
            result.setOverallLevel(parseLevel(overallLevelStr));

            result.setOverallFeedback(root.path("overall_feedback").asText(""));

            // Parse strengths
            result.setStrengths(toJsonArray(root.path("strengths")));

            // Parse improvements
            result.setImprovements(toJsonArray(root.path("improvements")));

            // Parse criteria
            JsonNode criteria = root.path("criteria");
            for (Map.Entry<String, String> entry : CRITERION_KEY_TO_NAME.entrySet()) {
                String jsonKey = entry.getKey();
                String displayName = entry.getValue();
                JsonNode criterionNode = criteria.path(jsonKey);

                if (!criterionNode.isMissingNode()) {
                    CriterionScore cs = parseCriterionScore(criterionNode, displayName, result);
                    result.getCriterionScores().add(cs);
                } else {
                    log.warn("Missing criterion '{}' in LLM response, creating default", jsonKey);
                    CriterionScore cs = createDefaultCriterionScore(displayName, result);
                    result.getCriterionScores().add(cs);
                }
            }

            // If overall score was not provided or seems wrong, recalculate from criteria
            if (result.getCriterionScores().size() == 3) {
                int calculatedAvg = (int) Math.round(
                        result.getCriterionScores().stream()
                                .mapToInt(CriterionScore::getScore)
                                .average()
                                .orElse(18)
                );
                int snappedAvg = snapToValidScore(calculatedAvg);
                result.setOverallScore(snappedAvg);
                result.setOverallLevel(levelFromScore(snappedAvg));
            }

            log.info("Parsed LLM response: overall_score={}, overall_level={}, criteria={}",
                    result.getOverallScore(), result.getOverallLevel(),
                    result.getCriterionScores().size());

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM response as JSON, returning partial result. Error: {}",
                    e.getMessage());
            setDefaults(result);
        }

        return result;
    }

    /**
     * Extract JSON from the LLM response by finding the first '{' and last '}'.
     * Handles cases where the LLM wraps JSON in markdown code fences or extra text.
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');

        if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
            log.warn("No valid JSON object found in LLM response");
            return "{}";
        }

        return response.substring(firstBrace, lastBrace + 1);
    }

    /**
     * Parse a single criterion node from the LLM JSON into a CriterionScore entity.
     */
    private CriterionScore parseCriterionScore(JsonNode node, String criterionName,
                                                EvaluationResult evaluationResult) {
        int score = snapToValidScore(node.path("score").asInt(18));
        String levelStr = node.path("level").asText("COMPETENT");
        String justification = node.path("justification").asText("");
        String evidence = toJsonArray(node.path("evidence"));

        return CriterionScore.builder()
                .evaluationResult(evaluationResult)
                .criterionName(criterionName)
                .score(score)
                .level(parseLevel(levelStr))
                .justification(justification)
                .evidence(evidence)
                .subScores(null)
                .build();
    }

    /**
     * Create a default CriterionScore when the LLM response is missing a criterion.
     */
    private CriterionScore createDefaultCriterionScore(String criterionName,
                                                        EvaluationResult evaluationResult) {
        return CriterionScore.builder()
                .evaluationResult(evaluationResult)
                .criterionName(criterionName)
                .score(PerformanceLevel.COMPETENT.getPoints())
                .level(PerformanceLevel.COMPETENT)
                .justification("Could not be determined from LLM response")
                .evidence("[]")
                .subScores(null)
                .build();
    }

    /**
     * Set default values when JSON parsing fails entirely.
     */
    private void setDefaults(EvaluationResult result) {
        result.setOverallScore(PerformanceLevel.COMPETENT.getPoints());
        result.setOverallLevel(PerformanceLevel.COMPETENT);
        result.setOverallFeedback("LLM evaluation completed but response could not be fully parsed.");
        result.setStrengths("[]");
        result.setImprovements("[\"Response parsing failed - manual review recommended\"]");

        for (Map.Entry<String, String> entry : CRITERION_KEY_TO_NAME.entrySet()) {
            result.getCriterionScores().add(
                    createDefaultCriterionScore(entry.getValue(), result));
        }
    }

    /**
     * Snap a raw integer score to the nearest valid rubric score (6, 12, 18, 24, 30).
     */
    private int snapToValidScore(int raw) {
        int[] validScores = {6, 12, 18, 24, 30};
        int closest = validScores[0];
        int minDiff = Math.abs(raw - closest);

        for (int score : validScores) {
            int diff = Math.abs(raw - score);
            if (diff < minDiff) {
                minDiff = diff;
                closest = score;
            }
        }
        return closest;
    }

    /**
     * Map a rubric score to its PerformanceLevel.
     */
    private PerformanceLevel levelFromScore(int score) {
        return switch (score) {
            case 30 -> PerformanceLevel.EXCELLENT;
            case 24 -> PerformanceLevel.PROFICIENT;
            case 18 -> PerformanceLevel.COMPETENT;
            case 12 -> PerformanceLevel.DEVELOPING;
            default -> PerformanceLevel.INADEQUATE;
        };
    }

    /**
     * Parse a PerformanceLevel from a string, defaulting to COMPETENT if unrecognized.
     */
    private PerformanceLevel parseLevel(String level) {
        try {
            return PerformanceLevel.valueOf(level.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognized performance level '{}', defaulting to COMPETENT", level);
            return PerformanceLevel.COMPETENT;
        }
    }

    /**
     * Convert a JsonNode array to a JSON array string. If the node is not an array,
     * wraps any text values in a single-element array.
     */
    private String toJsonArray(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "[]";
        }
        try {
            if (node.isArray()) {
                List<String> items = new ArrayList<>();
                node.forEach(item -> items.add(item.asText()));
                return objectMapper.writeValueAsString(items);
            } else if (node.isTextual()) {
                return objectMapper.writeValueAsString(List.of(node.asText()));
            }
            return "[]";
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize JSON array", e);
            return "[]";
        }
    }
}
