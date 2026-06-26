package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.evaluation.llm.*;
import com.capstone.eval.model.*;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.ParsedDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 3-round verification chain (decision tree):
 * Round 1: Generate top-3 candidate scores with reasoning for each criterion
 * Round 2: Cross-check candidates against rule package, pick top-1 (early-exit if already clear)
 * Round 3: Final calibrated score with adjustment rationale (only if Round 2 was ambiguous)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationChainExecutor {

    private final LlmProviderFactory providerFactory;
    private final DynamicPromptBuilder dynamicPromptBuilder;
    private final EvidenceDigestBuilder evidenceDigestBuilder;
    private final ObjectMapper objectMapper;

    private static final String ROUND_TYPE_VERIFY_CANDIDATES = "VERIFY_CANDIDATES";
    private static final String ROUND_TYPE_VERIFY_CROSSCHECK = "VERIFY_CROSSCHECK";
    private static final String ROUND_TYPE_VERIFY_FINAL = "VERIFY_FINAL";

    public VerificationChainResult execute(
            EvaluationResult originalResult,
            Submission submission,
            RulePackage rulePackage,
            List<RulePackageItem> enabledRules,
            LlmConfig config
    ) {
        return execute(originalResult, submission, rulePackage, enabledRules, config, null);
    }

    public VerificationChainResult execute(
            EvaluationResult originalResult,
            Submission submission,
            RulePackage rulePackage,
            List<RulePackageItem> enabledRules,
            LlmConfig config,
            ParsedDocument parsedDocument
    ) {
        log.info("Starting verification chain for evaluation id={}", originalResult.getId());

        int[] validScores = dynamicPromptBuilder.getValidScores(rulePackage);
        String scoringScaleJson = buildScoringScaleContext(rulePackage);
        String criteriaContext = buildCriteriaContext(enabledRules);
        String originalResultJson = serializeOriginalResult(originalResult);

        // Build evidence digest from the document (compact context for verification)
        String evidenceDigest = "";
        if (parsedDocument != null) {
            evidenceDigest = evidenceDigestBuilder.buildFullDigest(
                    originalResult, parsedDocument, enabledRules);
            log.info("Evidence digest built: {} chars (vs full doc: {} words)",
                    evidenceDigest.length(), parsedDocument.getTotalWordCount());
        } else {
            log.warn("No parsed document available — verification rounds will lack document evidence");
        }

        List<EvaluationRound> rounds = new ArrayList<>();
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;

        // === Round 1: Generate top-3 candidate scores ===
        LocalDateTime r1Start = LocalDateTime.now();
        RoundOutput round1 = executeRound1(originalResultJson, criteriaContext,
                scoringScaleJson, evidenceDigest, config);
        rounds.add(buildVerificationRound(round1, 1, ROUND_TYPE_VERIFY_CANDIDATES, r1Start));
        totalPromptTokens += round1.promptTokens();
        totalCompletionTokens += round1.completionTokens();

        if (!round1.success()) {
            log.warn("Verification Round 1 failed, returning original result unchanged");
            return VerificationChainResult.unchanged(originalResult, rounds, totalPromptTokens, totalCompletionTokens);
        }

        // === Round 2: Cross-check against rule package ===
        LocalDateTime r2Start = LocalDateTime.now();
        RoundOutput round2 = executeRound2(round1.rawResponse(), criteriaContext, scoringScaleJson, config);
        rounds.add(buildVerificationRound(round2, 2, ROUND_TYPE_VERIFY_CROSSCHECK, r2Start));
        totalPromptTokens += round2.promptTokens();
        totalCompletionTokens += round2.completionTokens();

        if (!round2.success()) {
            log.warn("Verification Round 2 failed, returning original result unchanged");
            return VerificationChainResult.unchanged(originalResult, rounds, totalPromptTokens, totalCompletionTokens);
        }

        // Check early exit: if Round 2 is confident (single clear winner), skip Round 3
        boolean needsRound3 = checkNeedsRound3(round2.parsedJson());

        RoundOutput round3 = null;
        if (needsRound3) {
            // Build targeted digest for ambiguous criteria only (cheaper than full)
            List<String> ambiguousCriteria = extractAmbiguousCriteria(round2.parsedJson());
            String targetedDigest = "";
            if (parsedDocument != null && !ambiguousCriteria.isEmpty()) {
                targetedDigest = evidenceDigestBuilder.buildTargetedDigest(
                        originalResult, parsedDocument, ambiguousCriteria);
            }

            // === Round 3: Final calibrated judgment ===
            LocalDateTime r3Start = LocalDateTime.now();
            round3 = executeRound3(round1.rawResponse(), round2.rawResponse(),
                    criteriaContext, scoringScaleJson, targetedDigest, config);
            rounds.add(buildVerificationRound(round3, 3, ROUND_TYPE_VERIFY_FINAL, r3Start));
            totalPromptTokens += round3.promptTokens();
            totalCompletionTokens += round3.completionTokens();

            if (!round3.success()) {
                log.warn("Verification Round 3 failed, using Round 2 result");
            }
        } else {
            log.info("Round 2 converged with high confidence, skipping Round 3 (early exit)");
        }

        // Parse the final round output into an updated EvaluationResult
        RoundOutput finalOutput = (round3 != null && round3.success()) ? round3 : round2;
        EvaluationResult verifiedResult = parseVerificationResult(
                finalOutput, originalResult, submission, enabledRules, rulePackage, validScores);

        return new VerificationChainResult(
                verifiedResult, rounds, totalPromptTokens, totalCompletionTokens,
                needsRound3 ? 3 : 2, true);
    }

    private RoundOutput executeRound1(
            String originalResultJson, String criteriaContext,
            String scoringScaleJson, String evidenceDigest, LlmConfig config
    ) {
        String systemPrompt = """
                You are a senior academic evaluator performing a verification review. \
                You have been given an initial evaluation result AND relevant excerpts from \
                the student's document. Your task is to generate 3 candidate score options \
                for EACH criterion, considering different interpretations of the evidence.

                For each criterion, provide:
                - Option A (generous interpretation): the highest defensible score
                - Option B (moderate interpretation): the most likely score
                - Option C (strict interpretation): the lowest defensible score

                Each option must include a brief reasoning (1-2 sentences) explaining why \
                that score could be justified based on the ACTUAL DOCUMENT EVIDENCE provided.

                IMPORTANT: Base your reasoning on the document excerpts, not just the original \
                evaluation's justifications. The original evaluation may have missed evidence \
                or misinterpreted sections.
                """ + "\n\n## Scoring Scale\n" + scoringScaleJson +
                "\n\n## Criteria\n" + criteriaContext;

        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("## Original Evaluation Result\n```json\n");
        userPromptBuilder.append(originalResultJson);
        userPromptBuilder.append("\n```\n\n");

        if (!evidenceDigest.isBlank()) {
            userPromptBuilder.append("## Document Evidence (excerpts)\n");
            userPromptBuilder.append(evidenceDigest);
            userPromptBuilder.append("\n\n");
        }

        userPromptBuilder.append("""
                ## Instructions
                Review the original evaluation against the DOCUMENT EVIDENCE above. \
                Generate 3 candidate scores for each criterion. Consider whether the \
                original score is too generous, too strict, or appropriate based on \
                what the document actually demonstrates.

                Pay special attention to:
                - "Blind spot" sections that were not cited but may contain relevant evidence
                - Whether quoted evidence actually supports the assigned score level
                - Whether the level boundary reasoning is sound

                Respond with ONLY a JSON object:
                {
                  "candidates": {
                    "<criterion_key>": {
                      "option_a": { "score": <int>, "reasoning": "<cite specific document evidence>" },
                      "option_b": { "score": <int>, "reasoning": "<cite specific document evidence>" },
                      "option_c": { "score": <int>, "reasoning": "<cite specific document evidence>" }
                    }
                  },
                  "initial_observations": "<any notable patterns, missed evidence, or concerns>",
                  "blind_spot_findings": "<what was found in uncited sections, if anything>"
                }
                """);

        return callLlm(systemPrompt, userPromptBuilder.toString(), config, 1);
    }

    private RoundOutput executeRound2(
            String round1Response, String criteriaContext,
            String scoringScaleJson, LlmConfig config
    ) {
        String systemPrompt = """
                You are a rubric compliance checker. You have received candidate scores from \
                Round 1 of a verification process. Your task is to cross-check each candidate \
                against the specific rubric criteria and pick the ONE most appropriate score \
                for each criterion.

                For each criterion:
                1. Evaluate which candidate (A, B, or C) best matches the rubric level descriptors
                2. Cite the specific rubric requirement that the chosen score satisfies
                3. Explain why the other candidates are less appropriate
                4. Rate your confidence (0.0-1.0) in the chosen score

                If your confidence is >= 0.8 for ALL criteria, set "converged": true (Round 3 not needed).
                """ + "\n\n## Scoring Scale\n" + scoringScaleJson +
                "\n\n## Criteria Definitions\n" + criteriaContext;

        String userPrompt = """
                ## Round 1 Candidates
                ```
                %s
                ```

                ## Instructions
                Cross-check each candidate against the rubric. Pick the best one per criterion.

                Respond with ONLY a JSON object:
                {
                  "decisions": {
                    "<criterion_key>": {
                      "chosen_option": "a|b|c",
                      "chosen_score": <int>,
                      "rubric_justification": "<which rubric level descriptor matches and why>",
                      "rejection_reasoning": "<why the other options don't fit as well>",
                      "confidence": <0.0-1.0>
                    }
                  },
                  "converged": <true if all confidence >= 0.8, false otherwise>,
                  "ambiguous_criteria": ["<keys of criteria where confidence < 0.8>"]
                }
                """.formatted(round1Response);

        return callLlm(systemPrompt, userPrompt, config, 2);
    }

    private RoundOutput executeRound3(
            String round1Response, String round2Response,
            String criteriaContext, String scoringScaleJson,
            String targetedDigest, LlmConfig config
    ) {
        String systemPrompt = """
                You are the final adjudicator in a 3-round verification process. Rounds 1 and 2 \
                have generated candidates and cross-checked them against the rubric, but some \
                criteria remain ambiguous. Your task is to make the FINAL calibrated decision.

                For ambiguous criteria, consider:
                - The balance of evidence from both prior rounds
                - The targeted document excerpts provided for ambiguous criteria
                - Whether the boundary reasoning supports the higher or lower score
                - The overall coherence of the evaluation (are criterion scores consistent with each other?)

                You must produce a final, definitive score for EVERY criterion (not just ambiguous ones).
                """ + "\n\n## Scoring Scale\n" + scoringScaleJson +
                "\n\n## Criteria Definitions\n" + criteriaContext;

        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("## Round 1 (Candidates)\n```\n");
        userPromptBuilder.append(round1Response);
        userPromptBuilder.append("\n```\n\n## Round 2 (Cross-Check)\n```\n");
        userPromptBuilder.append(round2Response);
        userPromptBuilder.append("\n```\n\n");

        if (targetedDigest != null && !targetedDigest.isBlank()) {
            userPromptBuilder.append(targetedDigest);
            userPromptBuilder.append("\n\n");
        }

        userPromptBuilder.append("""
                ## Instructions
                Produce the final calibrated evaluation. For ambiguous criteria, use the \
                targeted document evidence above to resolve the uncertainty. For each criterion, \
                output the definitive score with full justification.

                Respond with ONLY a JSON object:
                {
                  "criteria": {
                    "<criterion_key>": {
                      "score": <int>,
                      "level": "<level name>",
                      "confidence": <0.0-1.0>,
                      "justification": "<final reasoning citing document evidence>",
                      "adjustment_note": "<if different from original, explain why>"
                    }
                  },
                  "overall_score": <int>,
                  "overall_level": "<level name>",
                  "verification_summary": "<2-3 sentence summary of what changed and why>",
                  "changes_from_original": [
                    { "criterion": "<key>", "original_score": <int>, "verified_score": <int>, "reason": "<why>" }
                  ]
                }
                """);

        return callLlm(systemPrompt, userPromptBuilder.toString(), config, 3);
    }

    private RoundOutput callLlm(String systemPrompt, String userPrompt, LlmConfig config, int roundNumber) {
        try {
            LlmProvider provider = resolveProvider(config);
            LlmOptions options = resolveOptions(config);

            List<LlmMessage> messages = List.of(
                    LlmMessage.system(systemPrompt),
                    LlmMessage.user(userPrompt)
            );

            LlmResponse response = provider.chat(messages, options);
            JsonNode parsed = tryParseJson(response.content());

            return new RoundOutput(
                    roundNumber,
                    response.content(),
                    userPrompt,
                    parsed,
                    response.promptTokens(),
                    response.completionTokens(),
                    parsed != null,
                    parsed == null ? "Failed to parse verification round " + roundNumber + " response as JSON" : null
            );
        } catch (Exception e) {
            log.error("Verification round {} failed: {}", roundNumber, e.getMessage(), e);
            return new RoundOutput(roundNumber, null, userPrompt, null, 0, 0, false, e.getMessage());
        }
    }

    private boolean checkNeedsRound3(JsonNode round2Json) {
        if (round2Json == null) return true;
        JsonNode converged = round2Json.get("converged");
        if (converged != null && converged.asBoolean(false)) {
            return false;
        }
        JsonNode ambiguous = round2Json.get("ambiguous_criteria");
        if (ambiguous != null && ambiguous.isArray() && ambiguous.isEmpty()) {
            return false;
        }
        return true;
    }

    private List<String> extractAmbiguousCriteria(JsonNode round2Json) {
        List<String> result = new ArrayList<>();
        if (round2Json == null) return result;
        JsonNode ambiguous = round2Json.get("ambiguous_criteria");
        if (ambiguous != null && ambiguous.isArray()) {
            for (JsonNode node : ambiguous) {
                result.add(node.asText());
            }
        }
        return result;
    }

    private EvaluationResult parseVerificationResult(
            RoundOutput finalOutput,
            EvaluationResult originalResult,
            Submission submission,
            List<RulePackageItem> enabledRules,
            RulePackage rulePackage,
            int[] validScores
    ) {
        JsonNode root = finalOutput.parsedJson();
        if (root == null) {
            log.warn("Cannot parse verification final output, returning original");
            return originalResult;
        }

        Map<String, String> keyToName = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .collect(Collectors.toMap(
                        item -> item.getRule().getRuleKey(),
                        item -> item.getRule().getName(),
                        (a, b) -> a
                ));

        int midScore = validScores[validScores.length / 2];
        List<CriterionScore> criterionScores = new ArrayList<>();

        // Try parsing from Round 3 format (criteria with score/level/justification)
        JsonNode criteriaNode = root.get("criteria");
        if (criteriaNode != null && criteriaNode.isObject()) {
            var fields = criteriaNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String ruleKey = entry.getKey();
                JsonNode criterionNode = entry.getValue();

                String name = keyToName.getOrDefault(ruleKey, ruleKey);
                int score = snapToValidScore(getInt(criterionNode, "score", midScore), validScores);
                String level = getString(criterionNode, "level", "");
                String justification = getString(criterionNode, "justification", "");
                Double confidence = getDouble(criterionNode, "confidence");
                String adjustmentNote = getString(criterionNode, "adjustment_note", "");

                if (!adjustmentNote.isBlank()) {
                    justification = justification + " [Verification: " + adjustmentNote + "]";
                }

                PerformanceLevel parsedLevel = level.isBlank()
                        ? PerformanceLevel.fromPoints(score, validScores)
                        : parseLevel(level, validScores);

                CriterionScore cs = CriterionScore.builder()
                        .criterionName(name)
                        .score(score)
                        .level(parsedLevel)
                        .justification(justification)
                        .confidence(confidence)
                        .build();
                criterionScores.add(cs);
            }
        } else {
            // Fallback: parse from Round 2 "decisions" format
            JsonNode decisions = root.get("decisions");
            if (decisions != null && decisions.isObject()) {
                var fields = decisions.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    String ruleKey = entry.getKey();
                    JsonNode decisionNode = entry.getValue();

                    String name = keyToName.getOrDefault(ruleKey, ruleKey);
                    int score = snapToValidScore(getInt(decisionNode, "chosen_score", midScore), validScores);
                    String justification = getString(decisionNode, "rubric_justification", "");
                    Double confidence = getDouble(decisionNode, "confidence");

                    PerformanceLevel parsedLevel = PerformanceLevel.fromPoints(score, validScores);

                    CriterionScore cs = CriterionScore.builder()
                            .criterionName(name)
                            .score(score)
                            .level(parsedLevel)
                            .justification(justification)
                            .confidence(confidence)
                            .build();
                    criterionScores.add(cs);
                }
            }
        }

        if (criterionScores.isEmpty()) {
            log.warn("No criterion scores parsed from verification, returning original");
            return originalResult;
        }

        int overallScore = computeAverageScore(criterionScores, validScores, midScore);
        PerformanceLevel overallLevel = PerformanceLevel.fromPoints(overallScore, validScores);

        String verificationSummary = getString(root, "verification_summary", "Verification completed.");
        String changesJson = serializeNode(root.get("changes_from_original"));

        int maxScoreValue = validScores[validScores.length - 1];

        double avgConfidence = criterionScores.stream()
                .filter(cs -> cs.getConfidence() != null)
                .mapToDouble(CriterionScore::getConfidence)
                .average().orElse(0.0);

        String rawResponses = finalOutput.rawResponse();

        EvaluationResult result = EvaluationResult.builder()
                .submission(submission)
                .method(EvaluationMethod.LLM)
                .overallScore(overallScore)
                .overallLevel(overallLevel)
                .overallFeedback(verificationSummary)
                .strengths(changesJson)
                .improvements(originalResult.getImprovements())
                .confidence(avgConfidence)
                .maxScore(maxScoreValue)
                .rawLlmResponse(rawResponses)
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

    private String buildScoringScaleContext(RulePackage rulePackage) {
        List<Map<String, Object>> scale = dynamicPromptBuilder.parseScoringScale(rulePackage);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> entry : scale) {
            sb.append(String.format("- %s: %d points — %s\n",
                    entry.get("level"),
                    ((Number) entry.get("points")).intValue(),
                    entry.getOrDefault("description", "")));
        }
        return sb.toString();
    }

    private String buildCriteriaContext(List<RulePackageItem> enabledRules) {
        StringBuilder sb = new StringBuilder();
        for (RulePackageItem item : enabledRules) {
            if (!Boolean.TRUE.equals(item.getEnabled())) continue;
            sb.append(String.format("### %s (key: %s, weight: %.1f)\n",
                    item.getRule().getName(), item.getRule().getRuleKey(), item.getWeight()));
            String desc = item.getRule().getDescription();
            if (desc != null && !desc.isBlank()) {
                sb.append(desc).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String serializeOriginalResult(EvaluationResult result) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("overall_score", result.getOverallScore());
            node.put("overall_level", result.getOverallLevel() != null ? result.getOverallLevel().name() : "");

            ObjectNode criteria = objectMapper.createObjectNode();
            for (CriterionScore cs : result.getCriterionScores()) {
                ObjectNode criterion = objectMapper.createObjectNode();
                criterion.put("score", cs.getScore());
                criterion.put("level", cs.getLevel() != null ? cs.getLevel().name() : "");
                criterion.put("justification", cs.getJustification() != null ? cs.getJustification() : "");
                if (cs.getConfidence() != null) criterion.put("confidence", cs.getConfidence());
                criteria.set(cs.getCriterionName(), criterion);
            }
            node.set("criteria", criteria);

            if (result.getRawLlmResponse() != null) {
                node.put("raw_evidence", result.getRawLlmResponse().length() > 2000
                        ? result.getRawLlmResponse().substring(0, 2000) + "..."
                        : result.getRawLlmResponse());
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to serialize original result: {}", e.getMessage());
            return "{}";
        }
    }

    private EvaluationRound buildVerificationRound(RoundOutput output, int roundNumber, String roundType, LocalDateTime startedAt) {
        return EvaluationRound.builder()
                .roundNumber(roundNumber)
                .roundType(roundType)
                .systemPrompt(null)
                .userPrompt(output.userPrompt())
                .rawResponse(output.rawResponse())
                .promptTokens(output.promptTokens())
                .completionTokens(output.completionTokens())
                .status(output.success() ? "SUCCESS" : "FAILED")
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build();
    }

    private int computeAverageScore(List<CriterionScore> scores, int[] validScores, int midScore) {
        if (scores.isEmpty()) return midScore;
        double avg = scores.stream().mapToInt(CriterionScore::getScore).average().orElse(midScore);
        return snapToValidScore((int) Math.round(avg), validScores);
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

    private LlmProvider resolveProvider(LlmConfig config) {
        if (config != null && config.getProvider() != null && !config.getProvider().isBlank()) {
            return providerFactory.getProvider(config.getProvider());
        }
        return providerFactory.getDefaultProvider();
    }

    private LlmOptions resolveOptions(LlmConfig config) {
        double temperature = (config != null && config.getTemperature() != null)
                ? config.getTemperature() : 0.2;
        int maxTokens = (config != null && config.getMaxTokens() != null)
                ? config.getMaxTokens() : 4096;
        String model = (config != null && config.getModel() != null)
                ? config.getModel() : null;
        return new LlmOptions(temperature, maxTokens, model);
    }

    private JsonNode tryParseJson(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            String json = extractJson(content);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse verification response as JSON: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
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
