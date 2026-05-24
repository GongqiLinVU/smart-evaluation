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
    private final ObjectMapper objectMapper;

    private static final int[] VALID_SCORES = {6, 12, 18, 24, 30};

    public EvaluationResult synthesize(
            List<RoundOutput> roundOutputs,
            RoundOutput synthesisOutput,
            Submission submission,
            List<RulePackageItem> enabledRules,
            EvaluationPlan plan
    ) {
        RoundOutput finalOutput = (synthesisOutput != null && synthesisOutput.success())
                ? synthesisOutput
                : getBestAvailableOutput(roundOutputs);

        if (finalOutput == null || !finalOutput.success()) {
            return buildFallbackResult(roundOutputs, submission, enabledRules);
        }

        return parseOutputToResult(finalOutput, submission, enabledRules);
    }

    public EvaluationResult synthesizeSinglePass(
            RoundOutput output,
            Submission submission,
            List<RulePackageItem> enabledRules
    ) {
        if (!output.success()) {
            return buildFallbackResult(List.of(output), submission, enabledRules);
        }
        return parseOutputToResult(output, submission, enabledRules);
    }

    private EvaluationResult parseOutputToResult(
            RoundOutput output,
            Submission submission,
            List<RulePackageItem> enabledRules
    ) {
        JsonNode root = output.parsedJson();
        if (root == null) {
            return buildFallbackResult(List.of(output), submission, enabledRules);
        }

        Map<String, String> keyToName = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .collect(Collectors.toMap(
                        item -> item.getRule().getRuleKey(),
                        item -> item.getRule().getName(),
                        (a, b) -> a
                ));

        List<CriterionScore> criterionScores = new ArrayList<>();
        JsonNode criteriaNode = root.get("criteria");
        if (criteriaNode != null && criteriaNode.isObject()) {
            var fields = criteriaNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String ruleKey = entry.getKey();
                JsonNode criterionNode = entry.getValue();

                String name = keyToName.getOrDefault(ruleKey, ruleKey);
                int score = snapToValidScore(getInt(criterionNode, "score", 18));
                String level = getString(criterionNode, "level", "COMPETENT");
                String justification = getString(criterionNode, "justification", "");
                Double confidence = getDouble(criterionNode, "confidence");
                String evidence = serializeNode(criterionNode.get("evidence"));
                String suggestions = serializeNode(criterionNode.get("suggestions"));

                CriterionScore cs = CriterionScore.builder()
                        .criterionName(name)
                        .score(score)
                        .level(parseLevel(level))
                        .justification(justification)
                        .confidence(confidence)
                        .evidence(evidence)
                        .suggestions(suggestions)
                        .build();
                criterionScores.add(cs);
            }
        }

        int overallScore = snapToValidScore(getInt(root, "overall_score",
                computeAverageScore(criterionScores)));
        String overallLevel = getString(root, "overall_level", "COMPETENT");
        String overallFeedback = getString(root, "overall_feedback", "");
        String strengths = serializeNode(root.get("strengths"));
        String improvements = serializeNode(root.get("improvements"));

        double avgConfidence = criterionScores.stream()
                .filter(cs -> cs.getConfidence() != null)
                .mapToDouble(CriterionScore::getConfidence)
                .average().orElse(0.0);

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.LLM)
                .overallScore(overallScore)
                .overallLevel(parseLevel(overallLevel))
                .overallFeedback(overallFeedback)
                .strengths(strengths)
                .improvements(improvements)
                .confidence(avgConfidence)
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
            List<RulePackageItem> enabledRules
    ) {
        log.warn("Building fallback result from partial round outputs");

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
            int avgScore = scores.isEmpty() ? 18 :
                    snapToValidScore((int) Math.round(scores.stream().mapToInt(i -> i).average().orElse(18)));

            CriterionScore cs = CriterionScore.builder()
                    .criterionName(entry.getValue())
                    .score(avgScore)
                    .level(PerformanceLevel.fromPoints(avgScore))
                    .justification("Score derived from partial round results (synthesis failed)")
                    .confidence(0.5)
                    .build();
            criterionScores.add(cs);
        }

        int overallScore = computeAverageScore(criterionScores);

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.LLM)
                .overallScore(overallScore)
                .overallLevel(PerformanceLevel.fromPoints(overallScore))
                .overallFeedback("Evaluation completed with partial results due to synthesis failure.")
                .confidence(0.5)
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
        if (scores.isEmpty()) return 18;
        double avg = scores.stream().mapToInt(CriterionScore::getScore).average().orElse(18);
        return snapToValidScore((int) Math.round(avg));
    }

    private int snapToValidScore(int raw) {
        int closest = VALID_SCORES[0];
        int minDist = Math.abs(raw - closest);
        for (int valid : VALID_SCORES) {
            int dist = Math.abs(raw - valid);
            if (dist < minDist) {
                minDist = dist;
                closest = valid;
            }
        }
        return closest;
    }

    private PerformanceLevel parseLevel(String level) {
        try {
            return PerformanceLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return PerformanceLevel.COMPETENT;
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
