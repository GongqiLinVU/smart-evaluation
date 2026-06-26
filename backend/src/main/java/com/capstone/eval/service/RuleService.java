package com.capstone.eval.service;

import com.capstone.eval.dto.RuleRequest;
import com.capstone.eval.model.Rule;
import com.capstone.eval.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;

    public List<Rule> getAll() {
        return ruleRepository.findAllByOrderByNameAsc();
    }

    public Rule getById(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));
    }

    public Rule getByKey(String ruleKey) {
        return ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleKey));
    }

    @Transactional
    public Rule create(RuleRequest request) {
        if (ruleRepository.existsByRuleKey(request.ruleKey())) {
            throw new RuntimeException("Rule with key '" + request.ruleKey() + "' already exists");
        }
        Rule rule = Rule.builder()
                .ruleKey(request.ruleKey())
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .llmCriterionPrompt(request.llmCriterionPrompt())
                .builtIn(false)
                .build();
        return ruleRepository.save(rule);
    }

    @Transactional
    public Rule update(Long id, RuleRequest request) {
        Rule rule = getById(id);
        if (!rule.getRuleKey().equals(request.ruleKey()) && ruleRepository.existsByRuleKey(request.ruleKey())) {
            throw new RuntimeException("Rule with key '" + request.ruleKey() + "' already exists");
        }
        rule.setRuleKey(request.ruleKey());
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setCategory(request.category());
        rule.setLlmCriterionPrompt(request.llmCriterionPrompt());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        Rule rule = getById(id);
        if (Boolean.TRUE.equals(rule.getBuiltIn())) {
            throw new RuntimeException("Cannot delete built-in rules");
        }
        ruleRepository.deleteById(id);
    }

    @Transactional
    public void seedBuiltInRules() {
        seedIfAbsent("logic_explanation", "Logic & Explanation",
                "Assesses how well the student explains the logic and reasoning behind their implementation choices. " +
                        "Evaluates section coverage, explanation depth, technical vocabulary, data flow indicators, " +
                        "decision rationale, and diagram support.",
                "Analysis",
                LOGIC_EXPLANATION_PROMPT);
        seedIfAbsent("methodology", "Methodology",
                "Assesses the quality of methodology description including architecture design, " +
                        "technology justification, development process, design patterns, and testing/validation.",
                "Process",
                METHODOLOGY_PROMPT);
        seedIfAbsent("implementation_detail", "Implementation Detail",
                "Assesses the level of concrete implementation detail — code snippets, file references, " +
                        "schema documentation, configuration detail, and section completeness.",
                "Technical",
                IMPLEMENTATION_DETAIL_PROMPT);
    }

    private void seedIfAbsent(String key, String name, String description, String category, String llmCriterionPrompt) {
        if (!ruleRepository.existsByRuleKey(key)) {
            Rule rule = Rule.builder()
                    .ruleKey(key)
                    .name(name)
                    .description(description)
                    .category(category)
                    .llmCriterionPrompt(llmCriterionPrompt)
                    .builtIn(true)
                    .build();
            ruleRepository.save(rule);
        } else {
            Rule existing = ruleRepository.findByRuleKey(key).orElse(null);
            if (existing != null && existing.getLlmCriterionPrompt() == null) {
                existing.setLlmCriterionPrompt(llmCriterionPrompt);
                ruleRepository.save(existing);
            }
        }
    }

    @Transactional
    public void seedProgressReportRules() {
        seedIfAbsent("meeting_diary", "Meeting Diary Consistency",
                "Evaluates regularity and detail of meeting diary entries — whether updated after every team meeting, " +
                        "with detailed descriptions of tasks performed and team contributions",
                "Diary",
                MEETING_DIARY_PROMPT);
        seedIfAbsent("progress_organization", "Progress Diary Organization",
                "Evaluates how well-organized, clear, and structured the diary is — task descriptions, " +
                        "team contributions, and logical flow of the progress narrative",
                "Diary",
                PROGRESS_ORGANIZATION_PROMPT);
        seedIfAbsent("completion_demo", "80% Completion Demonstration",
                "Evaluates whether the project demonstrates at least 80% completion — features stated as requirements, " +
                        "working functionality shown, results presented with mandatory textual explanation " +
                        "(screenshots alone are insufficient)",
                "Demo",
                COMPLETION_DEMO_PROMPT);
    }

    private static final String MEETING_DIARY_PROMPT = """
            ### Meeting Diary Consistency
            How consistently and thoroughly does the student maintain their progress diary after team meetings?

            - HD (10): Consistently updated after every team meeting; all entries are detailed \
            and comprehensive, showing continuous work and progress. Each entry clearly records \
            date, attendees, discussion points, and individual action items.
            - D (8): Regularly updated after most meetings; entries provide clear details about \
            progress and work. Occasional meetings may lack an entry but the overall record \
            is strong.
            - C (6): Updated after most meetings, but some entries lack detail or consistency \
            in tracking progress. Gaps in the record are noticeable.
            - P (4): Updated sporadically; entries lack sufficient detail and do not consistently \
            reflect team meetings or work done between sessions.
            - F (2): Few or no entries; project progress is not documented or shows minimal effort. \
            The diary is essentially empty or unusable as a progress record.
            """;

    private static final String PROGRESS_ORGANIZATION_PROMPT = """
            ### Progress Diary Organization
            How well-organized, clear, and structured is the progress diary?

            - HD (10): Diary is exceptionally well-organized, clear, and easy to follow. \
            Structured with detailed descriptions of tasks, team contributions, and \
            milestones. Each entry has a logical flow and connects to previous entries.
            - D (8): Diary is well-organized with clear descriptions of tasks and team \
            contributions, though slightly less detailed. Structure is consistent \
            and navigation is straightforward.
            - C (6): Diary is organized but lacks consistency in clarity and depth. \
            Some tasks and contributions are vague or poorly categorized. \
            Structure exists but is uneven.
            - P (4): Diary is poorly organized with minimal clarity. Tasks and \
            contributions are poorly described. No consistent structure or format \
            between entries.
            - F (2): Diary is unorganized, unclear, and lacks meaningful content or \
            structure. Entries (if any) are incoherent or unstructured.
            """;

    private static final String COMPLETION_DEMO_PROMPT = """
            ### 80% Completion Demonstration
            Does the submission demonstrate at least 80% project completion with clear evidence \
            of working features?

            Evaluation criteria:
            1. REQUIREMENTS: Are the planned features/requirements clearly stated so the reader \
            knows what "100%" means?
            2. FUNCTIONALITY: Is there evidence that key features are operational (not just \
            planned or in-progress)?
            3. RESULTS WITH EXPLANATION: Are results shown (screenshots, outputs, logs) AND \
            accompanied by textual explanation of what is being demonstrated and how it \
            proves the feature works? Screenshots without explanation underneath are \
            NOT sufficient and should be penalized.
            4. SCOPE COVERAGE: Does the demonstrated work cover ~80% of the stated requirements?

            Scoring:
            - HD (10): Project demonstrates at least 80% completion with exceptional progress. \
            All key functionalities are fully operational. Results are shown with clear, \
            detailed explanation of what each screenshot/output demonstrates. The work \
            exceeds expectations, showing innovation, thorough testing, and attention to \
            detail. Requirements are explicitly stated and mapped to demonstrated features.
            - D (8): Project demonstrates 80% completion with significant progress. Most key \
            components work as expected with minor issues. Results are shown with adequate \
            explanation. Evidence of problem-solving and effective design decisions is clear, \
            but lacks some polish compared to HD.
            - C (6): Project shows moderate progress towards 80% completion. Key features are \
            present but may not function smoothly. Results are shown but explanations are \
            thin or some screenshots lack accompanying text. Meets fundamental requirements \
            but leaves questions about full functionality.
            - P (4): Project approaches 80% but demonstrates limited progress. Some features \
            are incomplete or lack functionality. Results may be shown but with little to \
            no explanation of what they prove. Requirements are unclear, making it hard \
            to assess actual completion percentage.
            - F (2): Project does not demonstrate sufficient progress toward 80% completion. \
            Key features are missing or non-functional. Results are absent or presented \
            without any context. The submission does not provide evidence of a working system.

            IMPORTANT: If screenshots or images are present without textual explanation below them, \
            this MUST be noted as a deficiency. The student must explain what the screenshot shows, \
            what feature it demonstrates, and what the expected vs actual behavior is.
            """;

    private static final String LOGIC_EXPLANATION_PROMPT = """
            ### Logic Explanation
            How well does the student explain the logic behind the main functions in their project?

            - EXCELLENT (30): Provides a clear, comprehensive, and insightful explanation of the logic \
            behind each main function, demonstrating in-depth understanding.
            - PROFICIENT (24): Thorough explanation of logic with good clarity, showing solid understanding \
            of each function's purpose and structure.
            - COMPETENT (18): Sufficient explanation of the logic for each function, with some minor gaps \
            or lack of detail.
            - DEVELOPING (12): Basic explanation of the logic, with limited depth, but overall \
            understanding is evident.
            - INADEQUATE (6): Fails to explain the logic or presents a disorganized, unclear understanding \
            of how the main functions work.
            """;

    private static final String METHODOLOGY_PROMPT = """
            ### Methodology
            How structured, clear, and justified is the methodology described in the report?

            - EXCELLENT (30): The methodology is explained in a highly structured, clear, and concise \
            manner, with strong justification for choices made.
            - PROFICIENT (24): Clear explanation of the methodology with appropriate reasoning for most \
            decisions made.
            - COMPETENT (18): Methodology is described but lacks clarity or depth in some areas; \
            reasoning is partially justified.
            - DEVELOPING (12): Basic methodology is described, but there is little justification or \
            reflection on choices made.
            - INADEQUATE (6): No clear explanation of methodology, or the methodology provided is \
            inadequate or poorly justified.
            """;

    private static final String IMPLEMENTATION_DETAIL_PROMPT = """
            ### Implementation Detail
            How precise and complete are the implementation descriptions?

            - EXCELLENT (30): Detailed and precise implementation steps are provided, showing a complete \
            understanding of how each main function was implemented.
            - PROFICIENT (24): Clear and mostly detailed description of implementation steps, \
            demonstrating a solid understanding of the function implementation.
            - COMPETENT (18): Adequate explanation of implementation, but with some minor gaps or lack \
            of technical detail.
            - DEVELOPING (12): Basic implementation steps are mentioned but lack clarity or thoroughness.
            - INADEQUATE (6): The implementation is poorly explained, with little to no detail on how \
            the functions were implemented.
            """;
}
