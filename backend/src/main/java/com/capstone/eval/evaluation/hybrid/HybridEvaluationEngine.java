package com.capstone.eval.evaluation.hybrid;

import com.capstone.eval.evaluation.EvaluationEngine;
import com.capstone.eval.evaluation.hybrid.model.*;
import com.capstone.eval.evaluation.llm.*;
import com.capstone.eval.evaluation.llm.multiround.DynamicPromptBuilder;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.*;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.ParsedDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HybridEvaluationEngine implements EvaluationEngine {

    private final EvidenceExtractor evidenceExtractor;
    private final EvidenceRuleEngine evidenceRuleEngine;
    private final DynamicPromptBuilder dynamicPromptBuilder;
    private final LlmProviderFactory llmProviderFactory;
    private final ObjectMapper objectMapper;

    @Override
    public EvaluationResult evaluate(ParsedDocument document, Submission submission) {
        throw new EvaluationException("Hybrid evaluation requires a RulePackage and LlmConfig");
    }

    public EvaluationResult evaluate(ParsedDocument document, Submission submission,
                                     RulePackage rulePackage, LlmConfig llmConfig) {
        log.info("Starting HYBRID evaluation for submission id={}", submission.getId());

        List<RulePackageItem> enabledItems = resolveEnabledItems(rulePackage);
        if (enabledItems.isEmpty()) {
            throw new EvaluationException("No enabled rule package items with evidence questions");
        }

        List<EvidenceReport> reports = new ArrayList<>();
        List<DecisionTrace> traces = new ArrayList<>();
        List<EvaluationRound> rounds = new ArrayList<>();

        for (int i = 0; i < enabledItems.size(); i++) {
            RulePackageItem item = enabledItems.get(i);
            LocalDateTime startedAt = LocalDateTime.now();

            EvidenceReport report = evidenceExtractor.extract(document, item, llmConfig);
            reports.add(report);

            DecisionTrace trace = evidenceRuleEngine.evaluate(report, item);
            traces.add(trace);

            EvaluationRound round = buildRound(i + 1, item, report, trace, startedAt);
            rounds.add(round);
        }

        EvaluationResult result = buildResult(submission, rulePackage, enabledItems, traces, reports);

        for (EvaluationRound round : rounds) {
            round.setEvaluationResult(result);
            result.getRounds().add(round);
        }

        // Replace the mechanical score-dump with a natural language synthesis
        HybridFeedback feedback = generateOverallFeedback(result, llmConfig);
        result.setOverallFeedback(feedback.overallFeedback());
        result.setStrengths(feedback.strengths());
        result.setImprovements(feedback.improvements());

        log.info("HYBRID evaluation complete for submission id={}: overall={} ({})",
                submission.getId(), result.getOverallScore(), result.getOverallLevel());

        return result;
    }

    private List<RulePackageItem> resolveEnabledItems(RulePackage rulePackage) {
        if (rulePackage == null || rulePackage.getItems() == null) {
            return List.of();
        }
        return rulePackage.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> item.getEvidenceQuestions() != null)
                .filter(item -> item.getScoringRules() != null)
                .toList();
    }

    private boolean isFixedMarkRubric(List<RulePackageItem> items) {
        return items.stream().anyMatch(item -> item.getMaxPoints() != null);
    }

    private EvaluationResult buildResult(Submission submission, RulePackage rulePackage,
                                         List<RulePackageItem> items,
                                         List<DecisionTrace> traces,
                                         List<EvidenceReport> reports) {
        int[] validScores = resolveValidScores(rulePackage);
        boolean fixedMark = isFixedMarkRubric(items);

        List<CriterionScore> criterionScores = new ArrayList<>();
        double weightedSum = 0;
        double totalWeight = 0;

        for (int i = 0; i < items.size(); i++) {
            RulePackageItem item = items.get(i);
            DecisionTrace trace = traces.get(i);
            EvidenceReport report = reports.get(i);

            Map<String, String> ruleDescriptions = buildRuleDescriptionMap(item);
            Map<String, String> questionTexts = buildQuestionTextMap(item);

            int score;
            PerformanceLevel level;

            if (fixedMark && item.getMaxPoints() != null) {
                // Scale the evidence score (0..traceMax) to the criterion's fixed mark ceiling
                double ratio = trace.getMaxPoints() > 0 ? trace.getTotalPoints() / trace.getMaxPoints() : 0.0;
                score = (int) Math.round(ratio * item.getMaxPoints());
                score = Math.max(0, Math.min(score, item.getMaxPoints()));
                // Derive level from ratio against the package's valid scale
                int rubricEquiv = mapToRubricScore(ratio, validScores);
                level = PerformanceLevel.fromPoints(rubricEquiv, validScores);
                weightedSum += score;   // fixed-mark: sum raw scores, no weighting
            } else {
                double weight = item.getWeight() != null ? item.getWeight() : 1.0;
                double ratio = trace.getMaxPoints() > 0 ? trace.getTotalPoints() / trace.getMaxPoints() : 0.0;
                score = mapToRubricScore(ratio, validScores);
                level = PerformanceLevel.fromPoints(score, validScores);
                weightedSum += score * weight;
                totalWeight += weight;
            }

            CriterionScore cs = CriterionScore.builder()
                    .criterionName(item.getRule().getRuleKey())
                    .score(score)
                    .level(level)
                    .justification(buildJustification(trace, ruleDescriptions))
                    .evidence(serializeObservationsAsEvidence(report, trace, questionTexts))
                    .suggestions(serializeSuggestions(trace, ruleDescriptions))
                    .build();
            criterionScores.add(cs);
        }

        int overallScore;
        PerformanceLevel overallLevel;
        int maxScoreValue;

        if (fixedMark) {
            // Sum of per-criterion scores; maxScore = sum of maxPoints
            overallScore = (int) Math.round(weightedSum);
            maxScoreValue = items.stream()
                    .filter(item -> item.getMaxPoints() != null)
                    .mapToInt(RulePackageItem::getMaxPoints)
                    .sum();
            // Derive level from percentage against the rubric scale
            double pct = maxScoreValue > 0 ? (double) overallScore / maxScoreValue : 0.0;
            int rubricEquiv = mapToRubricScore(pct, validScores);
            overallLevel = PerformanceLevel.fromPoints(rubricEquiv, validScores);
        } else {
            maxScoreValue = validScores[validScores.length - 1];
            overallScore = snapToValidScore((int) Math.round(weightedSum / totalWeight), validScores);
            overallLevel = PerformanceLevel.fromPoints(overallScore, validScores);
        }

        String rawResponse = serializeReports(reports);

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.HYBRID)
                .overallScore(overallScore)
                .overallLevel(overallLevel)
                .overallFeedback("")
                .rawLlmResponse(rawResponse)
                .maxScore(maxScoreValue)
                .confidence(1.0)
                .criterionScores(new ArrayList<>())
                .rounds(new ArrayList<>())
                .build();

        for (CriterionScore cs : criterionScores) {
            cs.setEvaluationResult(result);
            result.getCriterionScores().add(cs);
        }

        return result;
    }

    private int[] resolveValidScores(RulePackage rulePackage) {
        if (rulePackage != null) {
            int[] scores = dynamicPromptBuilder.getValidScores(rulePackage);
            if (scores.length > 0) return scores;
        }
        return new int[]{6, 12, 18, 24, 30};
    }

    private int mapToRubricScore(double ratio, int[] validScores) {
        double normalizedScore = ratio * validScores[validScores.length - 1];
        return snapToValidScore((int) Math.round(normalizedScore), validScores);
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

    private EvaluationRound buildRound(int roundNumber, RulePackageItem item,
                                       EvidenceReport report, DecisionTrace trace,
                                       LocalDateTime startedAt) {
        return EvaluationRound.builder()
                .roundNumber(roundNumber)
                .roundType("HYBRID_EVIDENCE")
                .targetCriteria(serializeSafe(List.of(item.getRule().getRuleKey())))
                .rawResponse(serializeSafe(report))
                .systemPrompt(serializeSafe(trace))
                .status("COMPLETED")
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build();
    }

    private record HybridFeedback(String overallFeedback, String strengths, String improvements) {}

    private HybridFeedback generateOverallFeedback(EvaluationResult result, LlmConfig llmConfig) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are an academic assessor writing feedback for a capstone report.\n\n");
            prompt.append("The report was assessed across these criteria:\n\n");

            for (CriterionScore cs : result.getCriterionScores()) {
                prompt.append(String.format("- %s: %s\n", cs.getCriterionName(), cs.getLevel()));
                if (cs.getJustification() != null && !cs.getJustification().isBlank()) {
                    prompt.append("  Detail: ").append(cs.getJustification()).append("\n");
                }
            }

            prompt.append(String.format("\nOverall: %s (%d/%d)\n\n",
                    result.getOverallLevel(), result.getOverallScore(), result.getMaxScore()));

            prompt.append("""
                    Respond with ONLY a JSON object (no markdown fences):
                    {
                      "overall_feedback": "<2-3 sentence narrative summary of the report as a whole>",
                      "strengths": ["<top strength 1>", "<top strength 2>", "<top strength 3 if applicable>"],
                      "improvements": ["<top improvement suggestion 1>", "<top improvement suggestion 2>", "<top improvement suggestion 3 if applicable>"]
                    }

                    Rules:
                    - strengths: 2-3 specific things the student did well, based on the highest-scoring criteria
                    - improvements: 2-3 concrete, actionable suggestions tied to the lowest-scoring criteria
                    - overall_feedback: a cohesive narrative, do NOT just list scores
                    - Do not mention numeric scores anywhere
                    - Be specific, refer to criteria by name""");

            LlmProvider provider = (llmConfig != null && llmConfig.getProvider() != null)
                    ? llmProviderFactory.getProvider(llmConfig.getProvider())
                    : llmProviderFactory.getDefaultProvider();

            LlmOptions options = new LlmOptions(0.3, 600,
                    llmConfig != null ? llmConfig.getModel() : null);

            LlmResponse response = provider.chat(
                    List.of(LlmMessage.user(prompt.toString())),
                    options
            );

            String content = response.content().trim();
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start == -1 || end == -1) throw new EvaluationException("No JSON in synthesis response");

            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(content.substring(start, end + 1));

            String overallFeedback = root.path("overall_feedback").asText("").trim();
            String strengths = root.has("strengths") ? objectMapper.writeValueAsString(root.get("strengths")) : null;
            String improvements = root.has("improvements") ? objectMapper.writeValueAsString(root.get("improvements")) : null;

            log.info("Generated hybrid synthesis feedback ({} chars, {} strengths, {} improvements)",
                    overallFeedback.length(),
                    root.path("strengths").size(),
                    root.path("improvements").size());

            return new HybridFeedback(overallFeedback, strengths, improvements);

        } catch (Exception e) {
            log.warn("Failed to generate synthesis feedback via LLM, using fallback: {}", e.getMessage());
            return buildFallbackFeedback(result);
        }
    }

    private HybridFeedback buildFallbackFeedback(EvaluationResult result) {
        StringBuilder sb = new StringBuilder("Overall performance: ").append(result.getOverallLevel()).append(". ");
        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        for (CriterionScore cs : result.getCriterionScores()) {
            String level = cs.getLevel() != null ? cs.getLevel().name() : "";
            if (level.equals("EXCELLENT") || level.equals("PROFICIENT")) {
                strengths.add(cs.getCriterionName() + " demonstrated at " + level + " level");
            } else if (level.equals("DEVELOPING") || level.equals("INADEQUATE")) {
                improvements.add("Strengthen " + cs.getCriterionName() + " — currently " + level);
            }
        }
        try {
            return new HybridFeedback(
                    sb.toString(),
                    objectMapper.writeValueAsString(strengths),
                    objectMapper.writeValueAsString(improvements)
            );
        } catch (Exception ex) {
            return new HybridFeedback(sb.toString(), null, null);
        }
    }

    private Map<String, String> buildRuleDescriptionMap(RulePackageItem item) {
        Map<String, String> map = new HashMap<>();
        if (item.getScoringRules() == null) return map;
        try {
            ScoringRuleSet ruleSet = objectMapper.readValue(item.getScoringRules(), ScoringRuleSet.class);
            if (ruleSet.getRules() != null) {
                for (ScoringRule rule : ruleSet.getRules()) {
                    if (rule.getId() != null && rule.getDescription() != null) {
                        map.put(rule.getId(), rule.getDescription());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse scoring rules for description map: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, String> buildQuestionTextMap(RulePackageItem item) {
        Map<String, String> map = new HashMap<>();
        if (item.getEvidenceQuestions() == null) return map;
        try {
            EvidenceQuestionSet questionSet = objectMapper.readValue(
                    item.getEvidenceQuestions(), EvidenceQuestionSet.class);
            if (questionSet.getQuestions() != null) {
                for (EvidenceQuestion q : questionSet.getQuestions()) {
                    if (q.getId() != null && q.getText() != null) {
                        map.put(q.getId(), q.getText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse evidence questions for text map: {}", e.getMessage());
        }
        return map;
    }

    private String buildJustification(DecisionTrace trace, Map<String, String> ruleDescriptions) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Scored %.1f/%.0f — %s. ",
                trace.getTotalPoints(), trace.getMaxPoints(), trace.getPerformanceLevel()));

        List<String> achieved = new ArrayList<>();
        List<String> gaps = new ArrayList<>();

        for (DecisionTrace.RuleResult r : trace.getRulesTriggered()) {
            String label = ruleDescriptions.getOrDefault(r.getRuleId(), r.getRuleId());
            if (Boolean.TRUE.equals(r.getFired())) {
                achieved.add(label);
            } else if ("partial".equals(r.getFired())) {
                achieved.add(label + " (partial)");
            } else {
                gaps.add(label);
            }
        }

        if (!achieved.isEmpty()) {
            sb.append("Criteria met: ").append(String.join("; ", achieved)).append(". ");
        }
        if (!gaps.isEmpty()) {
            sb.append("Gaps: ").append(String.join("; ", gaps)).append(".");
        }
        return sb.toString();
    }

    private String serializeObservationsAsEvidence(EvidenceReport report, DecisionTrace trace,
                                                   Map<String, String> questionTexts) {
        List<Map<String, Object>> evidenceItems = new ArrayList<>();

        if (report.getObservations() != null) {
            for (Observation obs : report.getObservations()) {
                Map<String, Object> item = new LinkedHashMap<>();

                if (obs.getCitation() != null && obs.getCitation().getQuote() != null
                        && !obs.getCitation().getQuote().isBlank()) {
                    item.put("quote", obs.getCitation().getQuote());
                    if (obs.getCitation().getSectionName() != null) {
                        item.put("sectionName", obs.getCitation().getSectionName());
                    }
                    if (obs.getCitation().getSectionIndex() >= 0) {
                        item.put("sectionIndex", obs.getCitation().getSectionIndex());
                    }
                } else {
                    String questionLabel = questionTexts.getOrDefault(obs.getQuestionId(), obs.getQuestionId());
                    item.put("quote", questionLabel + " — Answer: " + obs.getAnswer());
                }

                String sentiment = determineSentiment(obs, trace);
                item.put("sentiment", sentiment);

                if (obs.getNote() != null && !obs.getNote().isBlank()) {
                    item.put("note", obs.getNote());
                }

                evidenceItems.add(item);
            }
        }

        return serializeSafe(evidenceItems);
    }

    private String determineSentiment(Observation obs, DecisionTrace trace) {
        if (trace.getRulesTriggered() == null || obs.getQuestionId() == null) return "positive";
        for (DecisionTrace.RuleResult r : trace.getRulesTriggered()) {
            if (r.getReason() != null && r.getReason().contains(obs.getQuestionId())) {
                if (Boolean.TRUE.equals(r.getFired()) || "partial".equals(r.getFired())) {
                    return "positive";
                }
                return "negative";
            }
        }
        return "positive";
    }

    private String serializeSuggestions(DecisionTrace trace, Map<String, String> ruleDescriptions) {
        List<String> suggestions = new ArrayList<>();
        for (DecisionTrace.RuleResult r : trace.getRulesTriggered()) {
            if (Boolean.FALSE.equals(r.getFired()) && r.getPoints() == 0) {
                String label = ruleDescriptions.getOrDefault(r.getRuleId(), r.getRuleId());
                suggestions.add("Not yet demonstrated: " + label);
            }
        }
        if (suggestions.isEmpty()) return null;
        return serializeSafe(suggestions);
    }

    private String serializeReports(List<EvidenceReport> reports) {
        return serializeSafe(reports);
    }

    private String serializeSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object: {}", e.getMessage());
            return "{}";
        }
    }
}
