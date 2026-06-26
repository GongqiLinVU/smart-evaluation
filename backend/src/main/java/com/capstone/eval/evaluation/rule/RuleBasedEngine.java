package com.capstone.eval.evaluation.rule;

import com.capstone.eval.evaluation.EvaluationEngine;
import com.capstone.eval.evaluation.llm.multiround.DynamicPromptBuilder;
import com.capstone.eval.model.CriterionScore;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.ParsedDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule-based evaluation engine that orchestrates three criterion-level rules
 * ({@link LogicExplanationRule}, {@link MethodologyRule}, {@link ImplementationDetailRule})
 * and aggregates their results into a single {@link EvaluationResult}.
 *
 * <p>The overall score is the arithmetic mean of the three criterion raw scores (0-100),
 * which is then mapped to a {@link PerformanceLevel} and converted to rubric points (max 30).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleBasedEngine implements EvaluationEngine {

    private final LogicExplanationRule logicRule;
    private final MethodologyRule methodologyRule;
    private final ImplementationDetailRule implementationRule;
    private final MeetingDiaryRule meetingDiaryRule;
    private final ProgressOrganizationRule progressOrganizationRule;
    private final CompletionDemoRule completionDemoRule;
    private final DynamicPromptBuilder dynamicPromptBuilder;
    private final ObjectMapper objectMapper;

    private static final Set<String> PROGRESS_RULE_KEYS = Set.of(
            "meeting_diary", "progress_organization", "completion_demo");

    public EvaluationResult evaluate(ParsedDocument document, Submission submission, RulePackage rulePackage) {
        log.info("Starting rule-based evaluation for submission id={} with rule package '{}'",
                submission.getId(), rulePackage != null ? rulePackage.getName() : "default");

        List<CriterionResult> results = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        boolean usePackage = rulePackage != null;

        // Run progress report rules from RulePackageItems
        if (usePackage && rulePackage.getItems() != null) {
            for (RulePackageItem item : rulePackage.getItems()) {
                if (!Boolean.TRUE.equals(item.getEnabled())) continue;
                if (item.getRule() == null) continue;
                String key = item.getRule().getRuleKey();
                if (!PROGRESS_RULE_KEYS.contains(key)) continue;

                double weight = item.getWeight() != null ? item.getWeight() : 1.0;
                CriterionResult result = evaluateProgressRule(key, document);
                if (result != null) {
                    results.add(result);
                    weights.add(weight);
                }
            }
        }

        // Run final report rules (if enabled)
        boolean logicEnabled = !usePackage || Boolean.TRUE.equals(rulePackage.getLogicEnabled());
        boolean methodologyEnabled = !usePackage || Boolean.TRUE.equals(rulePackage.getMethodologyEnabled());
        boolean implementationEnabled = !usePackage || Boolean.TRUE.equals(rulePackage.getImplementationEnabled());

        if (logicEnabled) {
            results.add(logicRule.evaluate(document));
            weights.add(usePackage ? rulePackage.getLogicWeight() : 1.0);
        }
        if (methodologyEnabled) {
            results.add(methodologyRule.evaluate(document));
            weights.add(usePackage ? rulePackage.getMethodologyWeight() : 1.0);
        }
        if (implementationEnabled) {
            results.add(implementationRule.evaluate(document));
            weights.add(usePackage ? rulePackage.getImplementationWeight() : 1.0);
        }

        double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
        double overallRawScore = 0;
        if (totalWeight > 0) {
            for (int i = 0; i < results.size(); i++) {
                overallRawScore += results.get(i).getRawScore() * (weights.get(i) / totalWeight);
            }
        }

        return buildResult(document, submission, results, overallRawScore, rulePackage);
    }

    private CriterionResult evaluateProgressRule(String ruleKey, ParsedDocument document) {
        return switch (ruleKey) {
            case "meeting_diary" -> meetingDiaryRule.evaluate(document);
            case "progress_organization" -> progressOrganizationRule.evaluate(document);
            case "completion_demo" -> completionDemoRule.evaluate(document);
            default -> null;
        };
    }

    @Override
    public EvaluationResult evaluate(ParsedDocument document, Submission submission) {
        log.info("Starting rule-based evaluation for submission id={}", submission.getId());

        // 1. Run each criterion rule
        CriterionResult logicResult = logicRule.evaluate(document);
        CriterionResult methodologyResult = methodologyRule.evaluate(document);
        CriterionResult implementationResult = implementationRule.evaluate(document);

        List<CriterionResult> results = List.of(logicResult, methodologyResult, implementationResult);

        // 2. Calculate overall raw score (average of the three criterion raw scores)
        double overallRawScore = results.stream()
                .mapToDouble(CriterionResult::getRawScore)
                .average()
                .orElse(0);

        return buildResult(document, submission, results, overallRawScore, null);
    }

    private EvaluationResult buildResult(ParsedDocument document, Submission submission,
                                         List<CriterionResult> results, double overallRawScore,
                                         RulePackage rulePackage) {
        int[] validScores = resolveValidScores(rulePackage);
        int maxScore = validScores[validScores.length - 1];

        PerformanceLevel overallLevel = PerformanceLevel.fromRawScore(overallRawScore);
        int overallPoints;
        if (maxScore <= 10) {
            overallLevel = PerformanceLevel.fromPoints(mapRawToPoints(overallRawScore, validScores), validScores);
            overallPoints = overallLevel.getPoints();
        } else {
            overallPoints = overallLevel.getPoints();
        }

        List<String> strengths = buildStrengths(results);
        List<String> improvements = buildImprovements(results);
        String overallFeedback = buildOverallFeedback(results, overallRawScore, overallLevel, maxScore);

        List<CriterionScore> criterionScores = new ArrayList<>();

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.RULE_BASED)
                .overallScore(overallPoints)
                .maxScore(maxScore)
                .overallLevel(overallLevel)
                .overallFeedback(overallFeedback)
                .strengths(toJson(strengths))
                .improvements(toJson(improvements))
                .confidence(null)
                .rawLlmResponse(null)
                .evaluatedAt(LocalDateTime.now())
                .criterionScores(criterionScores)
                .build();

        for (CriterionResult cr : results) {
            int criterionPoints;
            PerformanceLevel criterionLevel;
            if (maxScore <= 10) {
                criterionLevel = PerformanceLevel.fromPoints(mapRawToPoints(cr.getRawScore(), validScores), validScores);
                criterionPoints = criterionLevel.getPoints();
            } else {
                criterionLevel = cr.getLevel();
                criterionPoints = criterionLevel.getPoints();
            }
            CriterionScore cs = CriterionScore.builder()
                    .evaluationResult(evaluationResult)
                    .criterionName(cr.getCriterionName())
                    .score(criterionPoints)
                    .level(criterionLevel)
                    .justification(cr.getJustification())
                    .evidence(toJson(cr.getEvidence()))
                    .subScores(toJson(cr.getSubScores()))
                    .build();
            criterionScores.add(cs);
        }

        log.info("Rule-based evaluation complete for submission id={}: overall={}/{} ({})",
                submission.getId(), overallPoints, maxScore, overallLevel);

        return evaluationResult;
    }

    private int[] resolveValidScores(RulePackage rulePackage) {
        if (rulePackage != null) {
            int[] scores = dynamicPromptBuilder.getValidScores(rulePackage);
            if (scores.length > 0) return scores;
        }
        return new int[]{6, 12, 18, 24, 30};
    }

    private int mapRawToPoints(double rawScore, int[] validScores) {
        if (rawScore >= 85) return validScores[validScores.length - 1];
        if (rawScore >= 65) return validScores[Math.max(0, validScores.length - 2)];
        if (rawScore >= 45) return validScores[Math.max(0, validScores.length - 3)];
        if (rawScore >= 25) return validScores[Math.max(0, validScores.length - 4)];
        return validScores[0];
    }

    // -------------------------------------------------------------------
    // Strengths & Improvements extraction
    // -------------------------------------------------------------------

    /**
     * Identify strengths: any criterion signal that scored at PROFICIENT (65+) or above.
     */
    private List<String> buildStrengths(List<CriterionResult> results) {
        List<String> strengths = new ArrayList<>();
        for (CriterionResult cr : results) {
            if (cr.getRawScore() >= 65) {
                strengths.add(cr.getCriterionName() + " is strong (raw score "
                        + String.format("%.0f", cr.getRawScore()) + "/100)");
            }
            // Also highlight individual high sub-scores
            if (cr.getSubScores() != null) {
                cr.getSubScores().forEach((name, sub) -> {
                    if (sub.getScore() >= 80) {
                        strengths.add(cr.getCriterionName() + " > "
                                + formatSubScoreName(name) + ": " + sub.getNote());
                    }
                });
            }
        }
        if (strengths.isEmpty()) {
            strengths.add("The document was submitted and contains some structured content");
        }
        return strengths;
    }

    /**
     * Identify areas for improvement: any criterion signal that scored below COMPETENT (45).
     */
    private List<String> buildImprovements(List<CriterionResult> results) {
        List<String> improvements = new ArrayList<>();
        for (CriterionResult cr : results) {
            if (cr.getRawScore() < 45) {
                improvements.add(cr.getCriterionName() + " needs significant improvement (raw score "
                        + String.format("%.0f", cr.getRawScore()) + "/100)");
            }
            // Also flag weak sub-scores
            if (cr.getSubScores() != null) {
                cr.getSubScores().forEach((name, sub) -> {
                    if (sub.getScore() < 40) {
                        improvements.add(cr.getCriterionName() + " > "
                                + formatSubScoreName(name) + ": " + sub.getNote());
                    }
                });
            }
        }
        if (improvements.isEmpty()) {
            improvements.add("Continue strengthening each section with more evidence and detail");
        }
        return improvements;
    }

    // -------------------------------------------------------------------
    // Overall feedback
    // -------------------------------------------------------------------

    private String buildOverallFeedback(List<CriterionResult> results,
                                        double overallRaw, PerformanceLevel level, int maxScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule-based evaluation completed. ");

        // Summarise each criterion
        for (CriterionResult cr : results) {
            sb.append(String.format("%s scored %s (%d points, raw %.0f/100). ",
                    cr.getCriterionName(), cr.getLevel().name(),
                    cr.getPoints(), cr.getRawScore()));
        }

        sb.append(String.format("The overall assessment is %s with %d out of %d points " +
                        "(mean raw score %.1f/100). ",
                level.name(), level.getPoints(), maxScore, overallRaw));

        // Tailored closing advice based on level
        switch (level) {
            case EXCELLENT: case HD:
                sb.append("The report demonstrates strong technical writing across all criteria.");
                break;
            case PROFICIENT: case D:
                sb.append("The report is solid but could benefit from deeper evidence in weaker areas.");
                break;
            case COMPETENT: case C:
                sb.append("The report covers the basics but lacks depth in several areas. " +
                        "Adding more code examples, diagrams, and justification would strengthen it.");
                break;
            case DEVELOPING: case P:
                sb.append("The report requires substantial improvement. Focus on adding " +
                        "implementation details, methodology description, and design rationale.");
                break;
            case INADEQUATE: case F:
                sb.append("The report is significantly below expectations. Most sections need " +
                        "to be expanded with concrete technical content.");
                break;
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise to JSON", e);
            return "[]";
        }
    }

    /**
     * Convert snake_case sub-score keys to Title Case for human-readable output.
     * E.g. "section_coverage" becomes "Section Coverage".
     */
    private String formatSubScoreName(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
