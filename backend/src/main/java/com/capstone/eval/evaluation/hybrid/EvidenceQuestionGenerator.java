package com.capstone.eval.evaluation.hybrid;

import com.capstone.eval.evaluation.hybrid.model.EvidenceQuestionSet;
import com.capstone.eval.evaluation.hybrid.model.ScoringRuleSet;
import com.capstone.eval.evaluation.llm.*;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.Rule;
import com.capstone.eval.model.RulePackageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceQuestionGenerator {

    private final LlmProviderFactory providerFactory;
    private final ObjectMapper objectMapper;

    public GenerationResult generate(RulePackageItem item, LlmConfig llmConfig) {
        Rule rule = item.getRule();
        if (rule == null) {
            throw new EvaluationException("RulePackageItem has no associated Rule");
        }

        String prompt = buildGenerationPrompt(rule);
        LlmProvider provider = resolveProvider(llmConfig);
        LlmOptions options = buildOptions(llmConfig);

        log.info("Generating evidence questions for rule '{}' (key={})", rule.getName(), rule.getRuleKey());

        LlmResponse response = provider.chat(
                List.of(LlmMessage.system(SYSTEM_PROMPT), LlmMessage.user(prompt)),
                options
        );

        return parseResponse(response.content(), rule.getRuleKey());
    }

    private LlmProvider resolveProvider(LlmConfig llmConfig) {
        if (llmConfig != null && llmConfig.getProvider() != null) {
            return providerFactory.getProvider(llmConfig.getProvider());
        }
        return providerFactory.getDefaultProvider();
    }

    private LlmOptions buildOptions(LlmConfig llmConfig) {
        String model = (llmConfig != null && llmConfig.getModel() != null)
                ? llmConfig.getModel()
                : null;
        return new LlmOptions(0.3, 4096, model);
    }

    private String buildGenerationPrompt(Rule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Criterion: ").append(rule.getName()).append("\n");
        if (rule.getDescription() != null) {
            sb.append("Description: ").append(rule.getDescription()).append("\n");
        }
        if (rule.getLlmCriterionPrompt() != null) {
            sb.append("LLM Criterion Prompt: ").append(rule.getLlmCriterionPrompt()).append("\n");
        }
        sb.append("\nRule Key: ").append(rule.getRuleKey()).append("\n");
        sb.append("\nGenerate evidence questions and scoring rules for this criterion. ");
        sb.append("Use the rule key as a prefix for question IDs (e.g., ");
        sb.append(rule.getRuleKey().toUpperCase()).append("_01).\n");
        return sb.toString();
    }

    private GenerationResult parseResponse(String content, String ruleKey) {
        try {
            String jsonContent = extractJsonBlock(content);
            GenerationResult result = objectMapper.readValue(jsonContent, GenerationResult.class);

            if (result.getQuestions() == null || result.getQuestions().getQuestions() == null) {
                throw new EvaluationException("LLM response did not contain valid evidence questions");
            }
            if (result.getScoringRules() == null || result.getScoringRules().getRules() == null) {
                throw new EvaluationException("LLM response did not contain valid scoring rules");
            }

            log.info("Generated {} questions and {} scoring rules for criterion '{}'",
                    result.getQuestions().getQuestions().size(),
                    result.getScoringRules().getRules().size(),
                    ruleKey);

            return result;
        } catch (EvaluationException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluationException(
                    "Failed to parse LLM response for evidence question generation: " + e.getMessage(), e);
        }
    }

    private String extractJsonBlock(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new EvaluationException("No JSON object found in LLM response");
        }
        return content.substring(start, end + 1);
    }

    private static final String SYSTEM_PROMPT = """
            You are an expert educational assessment designer. Your task is to generate evidence \
            questions and scoring rules for evaluating student capstone reports.

            Given a rubric criterion, generate:
            1. A set of factual evidence questions (6-10) that a document reviewer should answer
            2. A set of scoring rules that map question answers to points

            Requirements for questions:
            - Each question must be answerable from the document alone
            - Use these types: BOOLEAN (true/false), TERNARY (yes/partial/no), COUNT (integer >= 0), LIKERT_5 (1-5)
            - Questions should progress from basic to advanced
            - Include at least one penalty question (checks for contradictions/errors)
            - Use the provided rule key as prefix for IDs (e.g., KEY_01, KEY_02)

            Requirements for scoring rules:
            - maxPoints should be 10
            - Each rule references a question ID in its condition
            - Conditions use simple expressions: ID == true, ID == 'yes', ID >= 3, etc.
            - Rules can have a "partial" alternative with fewer points
            - Include a levelMapping with thresholds for: Excellent (min 9), Proficient (min 7), Competent (min 5), Developing (min 3), Inadequate (min 0)

            Respond with a single JSON object in this exact structure:
            {
              "questions": {
                "questions": [
                  {"id": "...", "text": "...", "type": "BOOLEAN|TERNARY|COUNT|LIKERT_5", "required": true}
                ]
              },
              "scoringRules": {
                "maxPoints": 10,
                "rules": [
                  {"id": "SR_01", "description": "...", "condition": "...", "points": 2, "partial": {"condition": "...", "points": 1}}
                ],
                "levelMapping": {
                  "Excellent": {"min": 9.0},
                  "Proficient": {"min": 7.0},
                  "Competent": {"min": 5.0},
                  "Developing": {"min": 3.0},
                  "Inadequate": {"min": 0}
                }
              }
            }

            Output ONLY the JSON object. No markdown fences, no explanation.""";

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GenerationResult {
        private EvidenceQuestionSet questions;
        private ScoringRuleSet scoringRules;
    }
}
