package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.model.*;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.PerformanceLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SynthesisAggregator {

    private final RoundExecutor roundExecutor;
    private final DynamicPromptBuilder dynamicPromptBuilder;
    private final ObjectMapper objectMapper;

    private static final int[] DEFAULT_VALID_SCORES = {6, 12, 18, 24, 30};

    public EvaluationResult synthesize(
            List<RoundOutput> roundOutputs,
            RoundOutput synthesisOutput,
            Submission submission,
            List<RulePackageItem> enabledRules,
            EvaluationPlan plan
    ) {
        return synthesize(roundOutputs, synthesisOutput, submission, enabledRules, plan, null);
    }

    public EvaluationResult synthesize(
            List<RoundOutput> roundOutputs,
            RoundOutput synthesisOutput,
            Submission submission,
            List<RulePackageItem> enabledRules,
            EvaluationPlan plan,
            RulePackage rulePackage
    ) {
        RoundOutput finalOutput = (synthesisOutput != null && synthesisOutput.success())
                ? synthesisOutput
                : getBestAvailableOutput(roundOutputs);

        if (finalOutput == null || !finalOutput.success()) {
            return buildFallbackResult(roundOutputs, submission, enabledRules, rulePackage);
        }

        return parseOutputToResult(finalOutput, submission, enabledRules, rulePackage);
    }

    public EvaluationResult synthesizeSinglePass(
            RoundOutput output,
            Submission submission,
            List<RulePackageItem> enabledRules
    ) {
        return synthesizeSinglePass(output, submission, enabledRules, null);
    }

    public EvaluationResult synthesizeSinglePass(
            RoundOutput output,
            Submission submission,
            List<RulePackageItem> enabledRules,
            RulePackage rulePackage
    ) {
        if (!output.success()) {
            return buildFallbackResult(List.of(output), submission, enabledRules, rulePackage);
        }
        return parseOutputToResult(output, submission, enabledRules, rulePackage);
    }

    private EvaluationResult parseOutputToResult(
            RoundOutput output,
            Submission submission,
            List<RulePackageItem> enabledRules,
            RulePackage rulePackage
    ) {
        JsonNode root = output.parsedJson();
        if (root == null) {
            return buildFallbackResult(List.of(output), submission, enabledRules, rulePackage);
        }

        int[] validScores = resolveValidScores(rulePackage);

        Map<String, String> keyToName = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .collect(Collectors.toMap(
                        item -> item.getRule().getRuleKey(),
                        item -> item.getRule().getName(),
                        (a, b) -> a
                ));

        Map<String, Integer> keyToMaxPoints = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && item.getMaxPoints() != null)
                .collect(Collectors.toMap(
                        item -> item.getRule().getRuleKey(),
                        RulePackageItem::getMaxPoints,
                        (a, b) -> a
                ));

        int midScore = validScores[validScores.length / 2];

        List<CriterionScore> criterionScores = new ArrayList<>();
        JsonNode criteriaNode = root.get("criteria");
        if (criteriaNode != null && criteriaNode.isObject()) {
            var fields = criteriaNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String ruleKey = entry.getKey();
                JsonNode criterionNode = entry.getValue();

                String name = keyToName.getOrDefault(ruleKey, ruleKey);
                Integer criterionMax = keyToMaxPoints.get(ruleKey);
                int rawScore = getInt(criterionNode, "score", criterionMax != null ? criterionMax / 2 : midScore);
                int score = criterionMax != null
                        ? Math.max(0, Math.min(rawScore, criterionMax))
                        : snapToValidScore(rawScore, validScores);
                String level = getString(criterionNode, "level", "");
                String justification = getString(criterionNode, "justification", "");
                Double confidence = getDouble(criterionNode, "confidence");
                String evidence = serializeNode(criterionNode.get("evidence"));
                String suggestions = serializeNode(criterionNode.get("suggestions"));

                PerformanceLevel parsedLevel = level.isBlank()
                        ? PerformanceLevel.fromPoints(score, validScores)
                        : parseLevel(level, validScores);

                CriterionScore cs = CriterionScore.builder()
                        .criterionName(name)
                        .score(score)
                        .level(parsedLevel)
                        .justification(justification)
                        .confidence(confidence)
                        .evidence(evidence)
                        .suggestions(suggestions)
                        .build();
                criterionScores.add(cs);
            }
        }

        int computedOverall = computeAverageScore(criterionScores, rulePackage);
        int llmOverall = getInt(root, "overall_score", -1);
        if (llmOverall >= 0 && llmOverall != computedOverall) {
            log.warn("LLM overall_score={} differs from computed average={}. Using computed value.",
                    llmOverall, computedOverall);
        }
        int overallScore = computedOverall;
        String overallLevel = getString(root, "overall_level", "");
        String overallFeedback = getString(root, "overall_feedback", "");
        String strengths = serializeNode(root.get("strengths"));
        String improvements = serializeNode(root.get("improvements"));

        PerformanceLevel parsedOverallLevel = overallLevel.isBlank()
                ? PerformanceLevel.fromPoints(overallScore, validScores)
                : parseLevel(overallLevel, validScores);

        double avgConfidence = criterionScores.stream()
                .filter(cs -> cs.getConfidence() != null)
                .mapToDouble(CriterionScore::getConfidence)
                .average().orElse(0.0);

        int totalMaxScore = computeTotalMaxScore(rulePackage);
        int maxScoreValue = totalMaxScore > 0 ? totalMaxScore : validScores[validScores.length - 1];

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.LLM)
                .overallScore(overallScore)
                .overallLevel(parsedOverallLevel)
                .overallFeedback(overallFeedback)
                .strengths(strengths)
                .improvements(improvements)
                .confidence(avgConfidence)
                .maxScore(maxScoreValue)
                .rawLlmResponse(output.rawResponse())
                .evaluatedAt(LocalDateTime.now())
                .criterionScores(new ArrayList<>())
                .rounds(new ArrayList<>())
                .build();

        for (CriterionScore cs : criterionScores) {
            cs.setEvaluationResult(result);
            result.getCriterionScores().add(cs);
        }

        return result;
    }

    private EvaluationResult buildFallbackResult(
            List<RoundOutput> outputs,
            Submission submission,
            List<RulePackageItem> enabledRules,
            RulePackage rulePackage
    ) {
        log.warn("Building fallback result from partial round outputs");

        int[] validScores = resolveValidScores(rulePackage);
        int midScore = validScores[validScores.length / 2];

        Map<String, List<Integer>> scoresByKey = new HashMap<>();
        for (RoundOutput output : outputs) {
            if (!output.success() || output.parsedJson() == null) continue;
            JsonNode criteriaNode = output.parsedJson().get("criteria");
            if (criteriaNode == null) continue;
            var fields = criteriaNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                int score = getInt(entry.getValue(), "score", 0);
                if (score > 0) {
                    scoresByKey.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(score);
                }
            }
        }

        Map<String, String> keyToName = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .collect(Collectors.toMap(
                        item -> item.getRule().getRuleKey(),
                        item -> item.getRule().getName(),
                        (a, b) -> a
                ));

        List<CriterionScore> criterionScores = new ArrayList<>();
        for (var entry : keyToName.entrySet()) {
            List<Integer> scores = scoresByKey.getOrDefault(entry.getKey(), Collections.emptyList());
            int avgScore = scores.isEmpty() ? midScore :
                    snapToValidScore((int) Math.round(scores.stream().mapToInt(i -> i).average().orElse(midScore)), validScores);

            CriterionScore cs = CriterionScore.builder()
                    .criterionName(entry.getValue())
                    .score(avgScore)
                    .level(PerformanceLevel.fromPoints(avgScore, validScores))
                    .justification("Score derived from partial round results (synthesis failed)")
                    .confidence(0.5)
                    .build();
            criterionScores.add(cs);
        }

        int overallScore = computeAverageScore(criterionScores, rulePackage);
        int totalMaxScore = computeTotalMaxScore(rulePackage);
        int maxScoreValue = totalMaxScore > 0 ? totalMaxScore : validScores[validScores.length - 1];

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.LLM)
                .overallScore(overallScore)
                .overallLevel(PerformanceLevel.fromPoints(overallScore, validScores))
                .overallFeedback("Evaluation completed with partial results due to synthesis failure.")
                .confidence(0.5)
                .maxScore(maxScoreValue)
                .rawLlmResponse(outputs.stream()
                        .filter(RoundOutput::success)
                        .map(RoundOutput::rawResponse)
                        .collect(Collectors.joining("\n---\n")))
                .evaluatedAt(LocalDateTime.now())
                .criterionScores(new ArrayList<>())
                .rounds(new ArrayList<>())
                .build();

        for (CriterionScore cs : criterionScores) {
            cs.setEvaluationResult(result);
            result.getCriterionScores().add(cs);
        }

        return result;
    }

    private RoundOutput getBestAvailableOutput(List<RoundOutput> outputs) {
        return outputs.stream()
                .filter(RoundOutput::success)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private int computeAverageScore(List<CriterionScore> scores) {
        return computeAverageScore(scores, null);
    }

    private int computeAverageScore(List<CriterionScore> scores, RulePackage rulePackage) {
        int[] validScores = resolveValidScores(rulePackage);
        int midScore = validScores[validScores.length / 2];
        if (scores.isEmpty()) return midScore;
        // If per-criterion maxPoints is set on the items, sum raw scores directly.
        // The criteria list won't have maxPoints on the CriterionScore itself, so we
        // use a plain sum only when the rulePackage items indicate a fixed-mark rubric.
        if (isFixedMarkRubric(rulePackage)) {
            return scores.stream().mapToInt(CriterionScore::getScore).sum();
        }
        double avg = scores.stream().mapToInt(CriterionScore::getScore).average().orElse(midScore);
        return snapToValidScore((int) Math.round(avg), validScores);
    }

    private boolean isFixedMarkRubric(RulePackage rulePackage) {
        if (rulePackage == null || rulePackage.getItems() == null) return false;
        return rulePackage.getItems().stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.getEnabled()) && item.getMaxPoints() != null);
    }

    int computeTotalMaxScore(RulePackage rulePackage) {
        if (!isFixedMarkRubric(rulePackage)) return -1;
        return rulePackage.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && item.getMaxPoints() != null)
                .mapToInt(RulePackageItem::getMaxPoints)
                .sum();
    }

    private int[] resolveValidScores(RulePackage rulePackage) {
        if (rulePackage != null) {
            int[] scores = dynamicPromptBuilder.getValidScores(rulePackage);
            if (scores.length > 0) return scores;
        }
        return DEFAULT_VALID_SCORES;
    }

    private int snapToValidScore(int raw, int[] validScores) {
        int closest = validScores[0];
        int minDist = Math.abs(raw - closest);
        for (int valid : validScores) {
            int dist = Math.abs(raw - valid);
            if (dist < minDist) {
                minDist = dist;
                closest = valid;
            }
        }
        return closest;
    }

    private PerformanceLevel parseLevel(String level) {
        return parseLevel(level, null);
    }

    private PerformanceLevel parseLevel(String level, int[] validScores) {
        if (level == null || level.isBlank()) {
            return (validScores != null && validScores.length > 0 && validScores[validScores.length - 1] <= 10)
                    ? PerformanceLevel.C : PerformanceLevel.COMPETENT;
        }
        try {
            return PerformanceLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return (validScores != null && validScores.length > 0 && validScores[validScores.length - 1] <= 10)
                    ? PerformanceLevel.C : PerformanceLevel.COMPETENT;
        }
    }

    private int getInt(JsonNode node, String field, int defaultVal) {
        if (node == null || !node.has(field)) return defaultVal;
        return node.get(field).asInt(defaultVal);
    }

    private String getString(JsonNode node, String field, String defaultVal) {
        if (node == null || !node.has(field)) return defaultVal;
        return node.get(field).asText(defaultVal);
    }

    private Double getDouble(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asDouble();
    }

    private String serializeNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }
}
