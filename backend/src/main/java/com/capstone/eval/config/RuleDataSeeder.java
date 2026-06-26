package com.capstone.eval.config;

import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.Rule;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.repository.LlmConfigRepository;
import com.capstone.eval.repository.RulePackageItemRepository;
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
    private final RulePackageItemRepository rulePackageItemRepository;
    private final RuleRepository ruleRepository;
    private final LlmConfigRepository llmConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Seeding built-in rules...");
        ruleService.seedBuiltInRules();
        log.info("Built-in rules seeded.");

        seedDefaultRulePackage();
        seedProgressReportRulePackage();
        seedDefaultLlmConfig();
        seedProgressReportLlmConfig();
        seedHybridConfigs();
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

    private void seedProgressReportRulePackage() {
        if (rulePackageRepository.findAll().stream()
                .anyMatch(p -> "Progress Report Standard".equals(p.getName()))) {
            log.info("Progress Report rule package already exists, skipping seed.");
            return;
        }

        ruleService.seedProgressReportRules();

        List<Rule> progressRules = ruleRepository.findAll().stream()
                .filter(r -> r.getRuleKey().equals("meeting_diary")
                        || r.getRuleKey().equals("progress_organization")
                        || r.getRuleKey().equals("completion_demo"))
                .toList();

        if (progressRules.size() < 3) {
            log.warn("Not all progress report rules found, cannot seed progress package.");
            return;
        }

        RulePackage progressPackage = RulePackage.builder()
                .name("Progress Report Standard")
                .description("Rule package for progress reports — meeting diary, organization, and 80% completion demo")
                .logicEnabled(false)
                .methodologyEnabled(false)
                .implementationEnabled(false)
                .logicWeight(0.0)
                .methodologyWeight(0.0)
                .implementationWeight(0.0)
                .scoringScale(PROGRESS_SCORING_SCALE)
                .isDefault(false)
                .build();

        RulePackage saved = rulePackageRepository.save(progressPackage);

        for (Rule rule : progressRules) {
            RulePackageItem item = RulePackageItem.builder()
                    .rulePackage(saved)
                    .rule(rule)
                    .enabled(true)
                    .weight(1.0)
                    .build();
            saved.getItems().add(item);
        }

        rulePackageRepository.save(saved);
        log.info("Progress Report rule package '{}' seeded with {} rules.", saved.getName(), progressRules.size());
    }

    private static final String PROGRESS_SCORING_SCALE = """
            [{"level":"HD","points":10,"description":"Outstanding, comprehensive progress"},{"level":"D","points":8,"description":"Solid progress with minor gaps"},{"level":"C","points":6,"description":"Adequate progress with some gaps"},{"level":"P","points":4,"description":"Basic progress with significant gaps"},{"level":"F","points":2,"description":"Insufficient progress"}]""";

    private void seedDefaultLlmConfig() {
        var existing = llmConfigRepository.findByIsDefaultTrue().orElse(null);
        if (existing != null) {
            if (existing.getSystemPromptTemplate() == null) {
                existing.setSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
                existing.setOutputFormatTemplate(null);
                if (existing.getProvider() == null) {
                    existing.setProvider("deepseek");
                }
                llmConfigRepository.save(existing);
                log.info("Backfilled default LLM config with sample template.");
            } else if (!existing.getSystemPromptTemplate().contains("{{scoring_scale_table}}")) {
                existing.setSystemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE);
                existing.setOutputFormatTemplate(null);
                llmConfigRepository.save(existing);
                log.info("Updated default LLM config to use dynamic scoring placeholders.");
            } else {
                log.info("Default LLM config already exists, skipping seed.");
            }
            return;
        }

        LlmConfig defaultConfig = LlmConfig.builder()
                .name("Default")
                .systemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE)
                .temperature(0.1)
                .maxTokens(4096)
                .provider("deepseek")
                .isDefault(true)
                .build();

        llmConfigRepository.save(defaultConfig);
        log.info("Default LLM config seeded with sample template (provider=deepseek).");
    }

    private void seedProgressReportLlmConfig() {
        if (llmConfigRepository.findByName("Progress Report").isPresent()) {
            log.info("Progress Report LLM config already exists, skipping seed.");
            return;
        }

        LlmConfig progressConfig = LlmConfig.builder()
                .name("Progress Report")
                .systemPromptTemplate(DEFAULT_SYSTEM_PROMPT_TEMPLATE)
                .additionalContext("This is a progress report evaluation, not a final report. "
                        + "Focus on diary consistency, organization quality, and demonstration of 80% project completion. "
                        + "Screenshots without textual explanation MUST be penalized.")
                .temperature(0.1)
                .maxTokens(4096)
                .provider("deepseek")
                .isDefault(false)
                .build();

        llmConfigRepository.save(progressConfig);
        log.info("Progress Report LLM config seeded.");
    }

    private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
            You are an experienced IT capstone project assessor. Your task is to evaluate \
            a student's project report based on a specific rubric. You must be fair, \
            consistent, and evidence-based in your assessment.

            ## Rubric

            The report is evaluated on the following criteria. Each criterion is scored at one of \
            the defined performance levels:

            {{scoring_scale_table}}

            {{criteria_block}}

            ## Scoring Rules
            - You MUST pick one of the valid score values: {{valid_score_values}} for each criterion.
            - Provide specific evidence from the document to justify each score.
            - For each evidence item, reference the specific section where you found it.
            - Be objective and base your assessment only on what is written in the document.
            - Provide a confidence score (0.0 to 1.0) for each criterion indicating how certain you are.

            {{additional_context}}

            ## Output Format
            {{output_format}}
            """;

    // -------------------------------------------------------------------------
    // Hybrid config seeding
    // -------------------------------------------------------------------------

    private void seedHybridConfigs() {
        seedItemHybridConfig("logic_explanation",    LOGIC_EVIDENCE_QUESTIONS,       LOGIC_SCORING_RULES);
        seedItemHybridConfig("methodology",          METHODOLOGY_EVIDENCE_QUESTIONS,  METHODOLOGY_SCORING_RULES);
        seedItemHybridConfig("implementation_detail",IMPL_EVIDENCE_QUESTIONS,         IMPL_SCORING_RULES);
        seedItemHybridConfig("meeting_diary",        DIARY_EVIDENCE_QUESTIONS,        DIARY_SCORING_RULES);
        seedItemHybridConfig("progress_organization",PROGRESS_ORG_EVIDENCE_QUESTIONS, PROGRESS_ORG_SCORING_RULES);
        seedItemHybridConfig("completion_demo",      COMPLETION_EVIDENCE_QUESTIONS,   COMPLETION_SCORING_RULES);
    }

    private void seedItemHybridConfig(String ruleKey, String evidenceQuestionsJson, String scoringRulesJson) {
        ruleRepository.findByRuleKey(ruleKey).ifPresent(rule -> {
            List<RulePackageItem> items = rulePackageItemRepository.findAll().stream()
                    .filter(i -> i.getRule() != null && ruleKey.equals(i.getRule().getRuleKey()))
                    .toList();
            for (RulePackageItem item : items) {
                item.setEvidenceQuestions(evidenceQuestionsJson);
                item.setScoringRules(scoringRulesJson);
                rulePackageItemRepository.save(item);
                log.info("Seeded hybrid config for rule '{}' in package id={}", ruleKey, item.getRulePackage().getId());
            }
        });
    }

    // --- Logic & Explanation ---
    // Questions probe WHY decisions were made and HOW the solution works,
    // not just whether functions are listed.

    private static final String LOGIC_EVIDENCE_QUESTIONS = """
            {
              "questions": [
                {"id": "LOGIC_01", "text": "Does the student explain WHY specific design decisions were made (e.g., why a particular algorithm, data structure, or control flow was chosen over alternatives)?", "type": "TERNARY", "required": true},
                {"id": "LOGIC_02", "text": "Does the report explain HOW the solution works end-to-end, tracing how input is transformed into output across components?", "type": "TERNARY", "required": true},
                {"id": "LOGIC_03", "text": "Are the key engineering trade-offs or constraints that shaped the design explicitly discussed (e.g., performance vs simplicity, accuracy vs speed)?", "type": "TERNARY", "required": true},
                {"id": "LOGIC_04", "text": "Does the student explain the reasoning behind any non-obvious or complex logic (e.g., edge case handling, concurrency strategy, caching policy)?", "type": "TERNARY", "required": true},
                {"id": "LOGIC_05", "text": "On a scale of 1-5, how well does the report demonstrate the student's own understanding of the logic rather than just describing what the code does?", "type": "LIKERT_5", "required": true},
                {"id": "LOGIC_06", "text": "Does the report identify any known limitations or failure modes of the design and explain why they exist?", "type": "BOOLEAN", "required": false},
                {"id": "LOGIC_07", "text": "Does the student use diagrams, flowcharts, or pseudocode to illustrate HOW the logic works (not just what components exist)?", "type": "BOOLEAN", "required": false}
              ]
            }""";

    private static final String LOGIC_SCORING_RULES = """
            {
              "maxPoints": 10,
              "rules": [
                {"id": "LSR_01", "description": "Design rationale explained (WHY this approach)", "condition": "LOGIC_01 == 'yes'", "points": 3, "partial": {"condition": "LOGIC_01 == 'partial'", "points": 2}},
                {"id": "LSR_02", "description": "End-to-end solution walkthrough present (HOW it works)", "condition": "LOGIC_02 == 'yes'", "points": 2, "partial": {"condition": "LOGIC_02 == 'partial'", "points": 1}},
                {"id": "LSR_03", "description": "Engineering trade-offs discussed", "condition": "LOGIC_03 == 'yes'", "points": 2, "partial": {"condition": "LOGIC_03 == 'partial'", "points": 1}},
                {"id": "LSR_04", "description": "Non-obvious logic reasoning explained", "condition": "LOGIC_04 == 'yes'", "points": 1, "partial": {"condition": "LOGIC_04 == 'partial'", "points": 1}},
                {"id": "LSR_05", "description": "Report demonstrates genuine understanding (not just listing code)", "condition": "LOGIC_05 >= 4", "points": 2, "partial": {"condition": "LOGIC_05 >= 3", "points": 1}}
              ],
              "levelMapping": {
                "Excellent": {"min": 9.0}, "Proficient": {"min": 7.0},
                "Competent": {"min": 5.0}, "Developing": {"min": 3.0}, "Inadequate": {"min": 0.0}
              }
            }""";

    // --- Methodology ---

    private static final String METHODOLOGY_EVIDENCE_QUESTIONS = """
            {
              "questions": [
                {"id": "METH_01", "text": "Does the student explain WHY they chose their methodology or framework (e.g., why Agile over waterfall, why this architecture over alternatives)?", "type": "TERNARY", "required": true},
                {"id": "METH_02", "text": "Are technology or tool choices justified with reasoning (e.g., why this framework, database, or language for this specific problem)?", "type": "TERNARY", "required": true},
                {"id": "METH_03", "text": "Does the report describe HOW the methodology was actually applied in this project (not just define what the methodology is)?", "type": "TERNARY", "required": true},
                {"id": "METH_04", "text": "Does the report describe how the solution was validated or tested — and why those validation strategies were appropriate?", "type": "TERNARY", "required": true},
                {"id": "METH_05", "text": "Are any deviations from the planned methodology acknowledged and explained?", "type": "BOOLEAN", "required": false},
                {"id": "METH_06", "text": "On a scale of 1-5, how well does the methodology section go beyond textbook definitions to show reflective, project-specific reasoning?", "type": "LIKERT_5", "required": true},
                {"id": "METH_07", "text": "Does the student acknowledge limitations of their chosen methodology and how those limitations affected the project?", "type": "BOOLEAN", "required": false}
              ]
            }""";

    private static final String METHODOLOGY_SCORING_RULES = """
            {
              "maxPoints": 10,
              "rules": [
                {"id": "MSR_01", "description": "Methodology choice justified with reasons (WHY this approach)", "condition": "METH_01 == 'yes'", "points": 2, "partial": {"condition": "METH_01 == 'partial'", "points": 1}},
                {"id": "MSR_02", "description": "Technology/tool choices justified for this problem", "condition": "METH_02 == 'yes'", "points": 2, "partial": {"condition": "METH_02 == 'partial'", "points": 1}},
                {"id": "MSR_03", "description": "Methodology applied in practice (HOW it was used), not just defined", "condition": "METH_03 == 'yes'", "points": 2, "partial": {"condition": "METH_03 == 'partial'", "points": 1}},
                {"id": "MSR_04", "description": "Validation/testing strategy described and justified", "condition": "METH_04 == 'yes'", "points": 2, "partial": {"condition": "METH_04 == 'partial'", "points": 1}},
                {"id": "MSR_05", "description": "Reflective project-specific reasoning (beyond textbook definitions)", "condition": "METH_06 >= 4", "points": 2, "partial": {"condition": "METH_06 >= 3", "points": 1}}
              ],
              "levelMapping": {
                "Excellent": {"min": 9.0}, "Proficient": {"min": 7.0},
                "Competent": {"min": 5.0}, "Developing": {"min": 3.0}, "Inadequate": {"min": 0.0}
              }
            }""";

    // --- Implementation Detail ---

    private static final String IMPL_EVIDENCE_QUESTIONS = """
            {
              "questions": [
                {"id": "IMPL_01", "text": "Does the report explain the key engineering decisions made during implementation (e.g., why a particular data model, API design, or concurrency approach was chosen)?", "type": "TERNARY", "required": true},
                {"id": "IMPL_02", "text": "Does the report explain HOW critical or non-trivial parts of the system were built — not just listing components but describing the construction logic?", "type": "TERNARY", "required": true},
                {"id": "IMPL_03", "text": "Are problems encountered during implementation described, along with how and why they were resolved?", "type": "TERNARY", "required": true},
                {"id": "IMPL_04", "text": "Does the student explain how different components integrate or interact with each other (not just that they exist)?", "type": "TERNARY", "required": true},
                {"id": "IMPL_05", "text": "Are the limitations of the current implementation acknowledged (e.g., scalability constraints, known bugs, shortcuts taken)?", "type": "BOOLEAN", "required": false},
                {"id": "IMPL_06", "text": "On a scale of 1-5, how well does the implementation section convey the student's problem-solving process rather than just a catalogue of features?", "type": "LIKERT_5", "required": true},
                {"id": "IMPL_07", "text": "Does the report read like 'Here is function A, here is function B' (a feature catalogue) rather than explaining the engineering story?", "type": "BOOLEAN", "required": false}
              ]
            }""";

    private static final String IMPL_SCORING_RULES = """
            {
              "maxPoints": 10,
              "rules": [
                {"id": "ISR_01", "description": "Key engineering decisions explained with rationale", "condition": "IMPL_01 == 'yes'", "points": 3, "partial": {"condition": "IMPL_01 == 'partial'", "points": 2}},
                {"id": "ISR_02", "description": "Construction logic explained for non-trivial parts (HOW it was built)", "condition": "IMPL_02 == 'yes'", "points": 2, "partial": {"condition": "IMPL_02 == 'partial'", "points": 1}},
                {"id": "ISR_03", "description": "Implementation problems and resolutions described", "condition": "IMPL_03 == 'yes'", "points": 2, "partial": {"condition": "IMPL_03 == 'partial'", "points": 1}},
                {"id": "ISR_04", "description": "Component integration and interactions explained", "condition": "IMPL_04 == 'yes'", "points": 1, "partial": {"condition": "IMPL_04 == 'partial'", "points": 1}},
                {"id": "ISR_05", "description": "Problem-solving process conveyed (not a feature catalogue)", "condition": "IMPL_06 >= 4", "points": 2, "partial": {"condition": "IMPL_06 >= 3", "points": 1}},
                {"id": "ISR_06", "description": "Penalty: report is purely a feature catalogue with no engineering reasoning", "condition": "IMPL_07 == false", "points": 0}
              ],
              "levelMapping": {
                "Excellent": {"min": 9.0}, "Proficient": {"min": 7.0},
                "Competent": {"min": 5.0}, "Developing": {"min": 3.0}, "Inadequate": {"min": 0.0}
              }
            }""";

    // --- Meeting Diary Consistency ---

    private static final String DIARY_EVIDENCE_QUESTIONS = """
            {
              "questions": [
                {"id": "DIARY_01", "text": "How many diary/meeting entries are recorded in the submission?", "type": "COUNT", "required": true},
                {"id": "DIARY_02", "text": "Does each entry include the meeting date?", "type": "TERNARY", "required": true},
                {"id": "DIARY_03", "text": "Does each entry record attendees or team member names?", "type": "TERNARY", "required": true},
                {"id": "DIARY_04", "text": "Does each entry describe discussion points or decisions made?", "type": "TERNARY", "required": true},
                {"id": "DIARY_05", "text": "Does each entry list individual action items or task assignments?", "type": "TERNARY", "required": true},
                {"id": "DIARY_06", "text": "Are there visible gaps (multiple weeks with no entries)?", "type": "BOOLEAN", "required": false},
                {"id": "DIARY_07", "text": "On a scale of 1-5, how consistently and thoroughly are the diary entries maintained overall?", "type": "LIKERT_5", "required": true}
              ]
            }""";

    private static final String DIARY_SCORING_RULES = """
            {
              "maxPoints": 10,
              "rules": [
                {"id": "DSR_01", "description": "At least 5 diary entries present", "condition": "DIARY_01 >= 5", "points": 2, "partial": {"condition": "DIARY_01 >= 3", "points": 1}},
                {"id": "DSR_02", "description": "All entries include meeting dates", "condition": "DIARY_02 == 'yes'", "points": 1, "partial": {"condition": "DIARY_02 == 'partial'", "points": 1}},
                {"id": "DSR_03", "description": "All entries record attendees", "condition": "DIARY_03 == 'yes'", "points": 1, "partial": {"condition": "DIARY_03 == 'partial'", "points": 1}},
                {"id": "DSR_04", "description": "All entries describe discussion points/decisions", "condition": "DIARY_04 == 'yes'", "points": 2, "partial": {"condition": "DIARY_04 == 'partial'", "points": 1}},
                {"id": "DSR_05", "description": "All entries list individual action items", "condition": "DIARY_05 == 'yes'", "points": 2, "partial": {"condition": "DIARY_05 == 'partial'", "points": 1}},
                {"id": "DSR_06", "description": "No multi-week gaps in diary", "condition": "DIARY_06 == false", "points": 1},
                {"id": "DSR_07", "description": "Overall consistency Proficient or better (>=4)", "condition": "DIARY_07 >= 4", "points": 1}
              ],
              "levelMapping": {
                "Excellent": {"min": 9.0}, "Proficient": {"min": 7.0},
                "Competent": {"min": 5.0}, "Developing": {"min": 3.0}, "Inadequate": {"min": 0.0}
              }
            }""";

    // --- Progress Diary Organization ---

    private static final String PROGRESS_ORG_EVIDENCE_QUESTIONS = """
            {
              "questions": [
                {"id": "PORG_01", "text": "Does the diary use a consistent format or template across entries (e.g., date, team, tasks, outcomes)?", "type": "BOOLEAN", "required": true},
                {"id": "PORG_02", "text": "Are tasks and contributions clearly attributed to specific team members?", "type": "TERNARY", "required": true},
                {"id": "PORG_03", "text": "Does each entry show a logical connection or progression from the previous entry?", "type": "TERNARY", "required": true},
                {"id": "PORG_04", "text": "Are milestones or sprint goals mentioned and tracked across entries?", "type": "BOOLEAN", "required": false},
                {"id": "PORG_05", "text": "Is the language and writing in each entry clear and easy to follow?", "type": "TERNARY", "required": true},
                {"id": "PORG_06", "text": "On a scale of 1-5, how well-organized and structured is the diary as a whole?", "type": "LIKERT_5", "required": true}
              ]
            }""";

    private static final String PROGRESS_ORG_SCORING_RULES = """
            {
              "maxPoints": 10,
              "rules": [
                {"id": "POSR_01", "description": "Consistent format used across entries", "condition": "PORG_01 == true", "points": 2},
                {"id": "POSR_02", "description": "Tasks clearly attributed to team members", "condition": "PORG_02 == 'yes'", "points": 2, "partial": {"condition": "PORG_02 == 'partial'", "points": 1}},
                {"id": "POSR_03", "description": "Entries show logical progression", "condition": "PORG_03 == 'yes'", "points": 2, "partial": {"condition": "PORG_03 == 'partial'", "points": 1}},
                {"id": "POSR_04", "description": "Milestones or sprint goals tracked", "condition": "PORG_04 == true", "points": 1},
                {"id": "POSR_05", "description": "Clear and readable writing throughout", "condition": "PORG_05 == 'yes'", "points": 1, "partial": {"condition": "PORG_05 == 'partial'", "points": 1}},
                {"id": "POSR_06", "description": "Overall organization Proficient or better (>=4)", "condition": "PORG_06 >= 4", "points": 2, "partial": {"condition": "PORG_06 >= 3", "points": 1}}
              ],
              "levelMapping": {
                "Excellent": {"min": 9.0}, "Proficient": {"min": 7.0},
                "Competent": {"min": 5.0}, "Developing": {"min": 3.0}, "Inadequate": {"min": 0.0}
              }
            }""";

    // --- 80% Completion Demonstration ---

    private static final String COMPLETION_EVIDENCE_QUESTIONS = """
            {
              "questions": [
                {"id": "COMP_01", "text": "Are the planned project requirements or features explicitly listed in the submission?", "type": "BOOLEAN", "required": true},
                {"id": "COMP_02", "text": "How many distinct features or requirements are explicitly stated?", "type": "COUNT", "required": true},
                {"id": "COMP_03", "text": "Does the submission include screenshots, output logs, or other evidence of working features?", "type": "BOOLEAN", "required": true},
                {"id": "COMP_04", "text": "Are all screenshots or visual results accompanied by textual explanation of what is being demonstrated?", "type": "TERNARY", "required": true},
                {"id": "COMP_05", "text": "What proportion of the stated requirements appear to be operational or demonstrated?", "type": "TERNARY", "required": true},
                {"id": "COMP_06", "text": "Does the submission demonstrate any innovation, testing, or quality beyond basic requirements?", "type": "BOOLEAN", "required": false},
                {"id": "COMP_07", "text": "Are there screenshots or images present WITHOUT any accompanying textual explanation?", "type": "BOOLEAN", "required": false}
              ]
            }""";

    private static final String COMPLETION_SCORING_RULES = """
            {
              "maxPoints": 10,
              "rules": [
                {"id": "CSR_01", "description": "Requirements/features explicitly listed", "condition": "COMP_01 == true", "points": 1},
                {"id": "CSR_02", "description": "At least 5 features/requirements stated", "condition": "COMP_02 >= 5", "points": 1, "partial": {"condition": "COMP_02 >= 3", "points": 1}},
                {"id": "CSR_03", "description": "Screenshots or output evidence present", "condition": "COMP_03 == true", "points": 1},
                {"id": "CSR_04", "description": "All visual results have textual explanation", "condition": "COMP_04 == 'yes'", "points": 3, "partial": {"condition": "COMP_04 == 'partial'", "points": 2}},
                {"id": "CSR_05", "description": "Majority of stated requirements demonstrated as operational", "condition": "COMP_05 == 'yes'", "points": 3, "partial": {"condition": "COMP_05 == 'partial'", "points": 2}},
                {"id": "CSR_06", "description": "Demonstrates innovation or thorough testing", "condition": "COMP_06 == true", "points": 1},
                {"id": "CSR_07", "description": "No unexplained screenshots (penalty marker)", "condition": "COMP_07 == false", "points": 0}
              ],
              "levelMapping": {
                "Excellent": {"min": 9.0}, "Proficient": {"min": 7.0},
                "Competent": {"min": 5.0}, "Developing": {"min": 3.0}, "Inadequate": {"min": 0.0}
              }
            }""";
}
