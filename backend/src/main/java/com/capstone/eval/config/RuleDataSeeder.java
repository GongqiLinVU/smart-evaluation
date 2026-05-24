package com.capstone.eval.config;

import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.Rule;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.repository.LlmConfigRepository;
import com.capstone.eval.repository.RulePackageRepository;
import com.capstone.eval.repository.RuleRepository;
import com.capstone.eval.service.RuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleDataSeeder implements ApplicationRunner {

    private final RuleService ruleService;
    private final RulePackageRepository rulePackageRepository;
    private final RuleRepository ruleRepository;
    private final LlmConfigRepository llmConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Seeding built-in rules...");
        ruleService.seedBuiltInRules();
        log.info("Built-in rules seeded.");

        seedDefaultRulePackage();
        seedDefaultLlmConfig();
    }

    private void seedDefaultRulePackage() {
        if (rulePackageRepository.findByIsDefaultTrue().isPresent()) {
            log.info("Default rule package already exists, skipping seed.");
            return;
        }

        List<Rule> builtInRules = ruleRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getBuiltIn()))
                .toList();

        if (builtInRules.isEmpty()) {
            log.warn("No built-in rules found, cannot seed default rule package.");
            return;
        }

        RulePackage defaultPackage = RulePackage.builder()
                .name("Standard Evaluation")
                .description("Default rule package with all built-in criteria enabled (Logic, Methodology, Implementation)")
                .logicEnabled(true)
                .methodologyEnabled(true)
                .implementationEnabled(true)
                .logicWeight(1.0)
                .methodologyWeight(1.0)
                .implementationWeight(1.0)
                .isDefault(true)
                .build();

        RulePackage saved = rulePackageRepository.save(defaultPackage);

        for (Rule rule : builtInRules) {
            RulePackageItem item = RulePackageItem.builder()
                    .rulePackage(saved)
                    .rule(rule)
                    .enabled(true)
                    .weight(1.0)
                    .build();
            saved.getItems().add(item);
        }

        rulePackageRepository.save(saved);
        log.info("Default rule package '{}' seeded with {} rules.", saved.getName(), builtInRules.size());
    }

    private void seedDefaultLlmConfig() {
        var existing = llmConfigRepository.findByIsDefaultTrue().orElse(null);
        if (existing != null) {
            if (existing.getSystemPromptTemplate() == null) {
                existing.setSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
                existing.setOutputFormatTemplate(DEFAULT_OUTPUT_FORMAT_TEMPLATE);
                if (existing.getProvider() == null) {
                    existing.setProvider("deepseek");
                }
                llmConfigRepository.save(existing);
                log.info("Backfilled default LLM config with sample template.");
            } else {
                log.info("Default LLM config already exists, skipping seed.");
            }
            return;
        }

        LlmConfig defaultConfig = LlmConfig.builder()
                .name("Default")
                .systemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE)
                .outputFormatTemplate(DEFAULT_OUTPUT_FORMAT_TEMPLATE)
                .temperature(0.1)
                .maxTokens(4096)
                .provider("deepseek")
                .isDefault(true)
                .build();

        llmConfigRepository.save(defaultConfig);
        log.info("Default LLM config seeded with sample template (provider=deepseek).");
    }

    private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
            You are an experienced IT capstone project assessor. Your task is to evaluate \
            a student's final project report based on a specific rubric. You must be fair, \
            consistent, and evidence-based in your assessment.

            ## Rubric

            The report is evaluated on the following criteria. Each criterion is scored at one of \
            five performance levels:

            | Level       | Points | Description                          |
            |-------------|--------|--------------------------------------|
            | EXCELLENT   | 30     | Outstanding, comprehensive work      |
            | PROFICIENT  | 24     | Solid, thorough work with minor gaps |
            | COMPETENT   | 18     | Adequate work with some gaps         |
            | DEVELOPING  | 12     | Basic work with significant gaps     |
            | INADEQUATE  | 6      | Poor or missing work                 |

            {{criteria_block}}

            ## Scoring Rules
            - You MUST pick one of the five valid score values: 30, 24, 18, 12, or 6 for each criterion.
            - Provide specific evidence from the document to justify each score.
            - For each evidence item, reference the specific section where you found it.
            - Be objective and base your assessment only on what is written in the document.
            - Provide a confidence score (0.0 to 1.0) for each criterion indicating how certain you are.

            {{additional_context}}

            ## Output Format
            {{output_format}}
            """;

    private static final String DEFAULT_OUTPUT_FORMAT_TEMPLATE = """
            You MUST respond with ONLY a JSON object in the following format (no markdown fencing, \
            no additional text before or after):
            {
              "overall_score": <integer: average of criterion scores, rounded to nearest valid level>,
              "overall_level": "<EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE>",
              "criteria": {
                "<criterion_rule_key>": {
                  "score": <6|12|18|24|30>,
                  "level": "<EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE>",
                  "confidence": <0.0 to 1.0>,
                  "justification": "<2-4 sentences explaining the score>",
                  "evidence": [
                    {
                      "sectionName": "<name of the section where evidence was found>",
                      "sectionIndex": <0-based index of the section>,
                      "quote": "<direct quote or key point from the document>",
                      "sentiment": "<positive|negative>",
                      "note": "<brief explanation of why this evidence matters>"
                    }
                  ],
                  "suggestions": ["<actionable improvement suggestion tied to a specific section>"]
                }
              },
              "strengths": ["<strength 1>", "<strength 2>"],
              "improvements": ["<improvement 1>", "<improvement 2>"],
              "overall_feedback": "<2-4 sentence summary of the overall assessment>"
            }
            """;
}
