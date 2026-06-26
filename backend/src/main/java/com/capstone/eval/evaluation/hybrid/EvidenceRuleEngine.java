package com.capstone.eval.evaluation.hybrid;

import com.capstone.eval.evaluation.hybrid.model.*;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.RulePackageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceRuleEngine {

    private final ObjectMapper objectMapper;

    public DecisionTrace evaluate(EvidenceReport report, RulePackageItem item) {
        ScoringRuleSet ruleSet = deserializeScoringRules(item);
        if (ruleSet == null || ruleSet.getRules() == null) {
            throw new EvaluationException(
                    "No scoring rules configured for item: " + item.getId());
        }

        Map<String, Object> answers = buildAnswerMap(report);
        List<DecisionTrace.RuleResult> results = new ArrayList<>();
        double totalPoints = 0;

        for (ScoringRule rule : ruleSet.getRules()) {
            DecisionTrace.RuleResult result = evaluateRule(rule, answers);
            results.add(result);
            totalPoints += result.getPoints();
        }

        totalPoints = Math.max(0, Math.min(totalPoints, ruleSet.getMaxPoints()));

        String level = mapToLevel(totalPoints, ruleSet.getLevelMapping());
        String explanation = buildExplanation(report.getCriterionKey(), totalPoints,
                ruleSet.getMaxPoints(), level, results);

        DecisionTrace trace = DecisionTrace.builder()
                .criterionKey(report.getCriterionKey())
                .totalPoints(totalPoints)
                .maxPoints(ruleSet.getMaxPoints())
                .performanceLevel(level)
                .rulesTriggered(results)
                .explanation(explanation)
                .build();

        log.info("Rule evaluation for '{}': {}/{} points -> {}",
                report.getCriterionKey(), totalPoints, ruleSet.getMaxPoints(), level);

        return trace;
    }

    private ScoringRuleSet deserializeScoringRules(RulePackageItem item) {
        if (item.getScoringRules() == null) return null;
        try {
            return objectMapper.readValue(item.getScoringRules(), ScoringRuleSet.class);
        } catch (Exception e) {
            throw new EvaluationException("Failed to parse scoring rules for item " + item.getId(), e);
        }
    }

    private Map<String, Object> buildAnswerMap(EvidenceReport report) {
        Map<String, Object> answers = new HashMap<>();
        if (report.getObservations() == null) return answers;

        for (Observation obs : report.getObservations()) {
            if (obs.getQuestionId() != null && obs.getAnswer() != null) {
                answers.put(obs.getQuestionId(), obs.getAnswer());
            }
        }
        return answers;
    }

    private DecisionTrace.RuleResult evaluateRule(ScoringRule rule, Map<String, Object> answers) {
        boolean fullMatch = evaluateCondition(rule.getCondition(), answers);

        if (fullMatch) {
            return DecisionTrace.RuleResult.builder()
                    .ruleId(rule.getId())
                    .fired(true)
                    .points(rule.getPoints())
                    .reason(rule.getCondition() + " (matched)")
                    .build();
        }

        if (rule.getPartial() != null) {
            boolean partialMatch = evaluateCondition(rule.getPartial().getCondition(), answers);
            if (partialMatch) {
                return DecisionTrace.RuleResult.builder()
                        .ruleId(rule.getId())
                        .fired("partial")
                        .points(rule.getPartial().getPoints())
                        .reason(rule.getPartial().getCondition() + " (partial match)")
                        .build();
            }
        }

        return DecisionTrace.RuleResult.builder()
                .ruleId(rule.getId())
                .fired(false)
                .points(0)
                .reason(rule.getCondition() + " (not matched)")
                .build();
    }

    boolean evaluateCondition(String condition, Map<String, Object> answers) {
        if (condition == null || condition.isBlank()) return false;

        condition = condition.trim();

        // Parse: ID operator value
        String[] parts = parseCondition(condition);
        if (parts == null) {
            log.warn("Unparseable condition: '{}'", condition);
            return false;
        }

        String questionId = parts[0];
        String operator = parts[1];
        String valueStr = parts[2];

        Object answer = answers.get(questionId);
        if (answer == null) return false;

        return compare(answer, operator, valueStr);
    }

    private String[] parseCondition(String condition) {
        String[] operators = {"==", "!=", ">=", "<=", ">", "<"};
        for (String op : operators) {
            int idx = condition.indexOf(op);
            if (idx > 0) {
                String left = condition.substring(0, idx).trim();
                String right = condition.substring(idx + op.length()).trim();
                return new String[]{left, op, right};
            }
        }
        return null;
    }

    private boolean compare(Object answer, String operator, String valueStr) {
        // Remove quotes from value if present
        valueStr = valueStr.replace("'", "").replace("\"", "");

        // Boolean comparison
        if ("true".equalsIgnoreCase(valueStr) || "false".equalsIgnoreCase(valueStr)) {
            boolean expected = Boolean.parseBoolean(valueStr);
            boolean actual = toBoolean(answer);
            return switch (operator) {
                case "==" -> actual == expected;
                case "!=" -> actual != expected;
                default -> false;
            };
        }

        // String comparison (ternary values: yes/partial/no)
        if (valueStr.equals("yes") || valueStr.equals("partial") || valueStr.equals("no")) {
            String actual = answer.toString().toLowerCase().trim();
            return switch (operator) {
                case "==" -> actual.equals(valueStr);
                case "!=" -> !actual.equals(valueStr);
                default -> false;
            };
        }

        // Numeric comparison
        try {
            double expected = Double.parseDouble(valueStr);
            double actual = toDouble(answer);
            return switch (operator) {
                case "==" -> actual == expected;
                case "!=" -> actual != expected;
                case ">=" -> actual >= expected;
                case "<=" -> actual <= expected;
                case ">" -> actual > expected;
                case "<" -> actual < expected;
                default -> false;
            };
        } catch (NumberFormatException e) {
            // Fall back to string equality
            String actual = answer.toString().trim();
            return switch (operator) {
                case "==" -> actual.equalsIgnoreCase(valueStr);
                case "!=" -> !actual.equalsIgnoreCase(valueStr);
                default -> false;
            };
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        String s = value.toString().trim().toLowerCase();
        return "true".equals(s) || "yes".equals(s) || "1".equals(s);
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(value.toString().trim());
    }

    private String mapToLevel(double points, Map<String, ScoringRuleSet.LevelThreshold> levelMapping) {
        if (levelMapping == null || levelMapping.isEmpty()) {
            return mapToDefaultLevel(points, 10);
        }

        String bestLevel = "Inadequate";
        double bestMin = -1;

        for (Map.Entry<String, ScoringRuleSet.LevelThreshold> entry : levelMapping.entrySet()) {
            double min = entry.getValue().getMin();
            if (points >= min && min > bestMin) {
                bestMin = min;
                bestLevel = entry.getKey();
            }
        }
        return bestLevel;
    }

    private String mapToDefaultLevel(double points, double maxPoints) {
        double ratio = points / maxPoints;
        if (ratio >= 0.9) return "Excellent";
        if (ratio >= 0.7) return "Proficient";
        if (ratio >= 0.5) return "Competent";
        if (ratio >= 0.3) return "Developing";
        return "Inadequate";
    }

    private String buildExplanation(String criterionKey, double totalPoints,
                                    double maxPoints, String level,
                                    List<DecisionTrace.RuleResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Scored %.1f/%.0f (%s). ", totalPoints, maxPoints, level));

        List<String> achieved = new ArrayList<>();
        List<String> missed = new ArrayList<>();

        for (DecisionTrace.RuleResult r : results) {
            if (Boolean.TRUE.equals(r.getFired()) || "partial".equals(r.getFired())) {
                achieved.add(r.getReason());
            } else if (r.getPoints() == 0 && Boolean.FALSE.equals(r.getFired())) {
                missed.add(r.getReason());
            }
        }

        if (!achieved.isEmpty()) {
            sb.append("Achieved: ").append(String.join("; ", achieved.subList(0, Math.min(3, achieved.size())))).append(". ");
        }
        if (!missed.isEmpty()) {
            sb.append("Gaps: ").append(String.join("; ", missed.subList(0, Math.min(3, missed.size())))).append(".");
        }

        return sb.toString();
    }
}
