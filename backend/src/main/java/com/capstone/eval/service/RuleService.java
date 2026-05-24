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
