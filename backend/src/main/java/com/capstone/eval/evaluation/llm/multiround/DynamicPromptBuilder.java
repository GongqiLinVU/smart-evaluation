package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DynamicPromptBuilder {

    private static final int MAX_ROUND_CHARS = 60_000;

    private static final String DEFAULT_SYSTEM_TEMPLATE = """
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

    private static final String DEFAULT_OUTPUT_FORMAT = """
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

    public String buildSystemPrompt(LlmConfig config, List<RulePackageItem> enabledRules) {
        String template = (config != null && config.getSystemPromptTemplate() != null)
                ? config.getSystemPromptTemplate()
                : DEFAULT_SYSTEM_TEMPLATE;

        String criteriaBlock = buildCriteriaBlock(enabledRules);
        String outputFormat = (config != null && config.getOutputFormatTemplate() != null)
                ? config.getOutputFormatTemplate()
                : buildDefaultOutputFormat(enabledRules);
        String additionalContext = (config != null && config.getAdditionalContext() != null)
                ? "## Additional Context\n" + config.getAdditionalContext()
                : "";

        String prompt = template
                .replace("{{criteria_block}}", criteriaBlock)
                .replace("{{output_format}}", outputFormat)
                .replace("{{additional_context}}", additionalContext);

        log.info("Built dynamic system prompt: {} chars, {} criteria",
                prompt.length(), enabledRules.size());
        return prompt;
    }

    public String buildRoundUserPrompt(ParsedDocument document, RoundSpec roundSpec) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Document Overview\n");
        sb.append(String.format("- Total word count: %d\n", document.getTotalWordCount()));
        sb.append(String.format("- Total sections: %d\n", document.getSections().size()));
        sb.append(String.format("- Sections included in this evaluation: %d\n",
                roundSpec.sectionIndices().size()));
        sb.append(String.format("- Focus criteria: %s\n",
                String.join(", ", roundSpec.criteriaKeys())));
        sb.append("\n");

        sb.append("## Document Sections\n\n");

        List<DocumentSection> sections = document.getSections();
        for (int idx : roundSpec.sectionIndices()) {
            if (idx >= sections.size()) continue;
            DocumentSection section = sections.get(idx);
            String headingPrefix = "#".repeat(Math.min(section.getHeadingLevel(), 6));

            sb.append(String.format("### Section %d: %s %s\n", idx + 1, headingPrefix, section.getHeading()));
            sb.append(String.format("Word count: %d | Images: %d | Tables: %d | Code snippets: %d\n",
                    section.getWordCount(), section.getImageCount(),
                    section.getTableCount(), section.getCodeSnippets().size()));

            if (section.getTechnicalVocabularyDensity() > 0) {
                sb.append(String.format("Technical vocabulary density: %.2f\n",
                        section.getTechnicalVocabularyDensity()));
            }
            sb.append("\n");

            String content = section.getContent();
            if (content != null && !content.isBlank()) {
                if (sb.length() + content.length() > MAX_ROUND_CHARS - 2000) {
                    int remaining = MAX_ROUND_CHARS - sb.length() - 2000;
                    if (remaining > 200) {
                        sb.append(content, 0, remaining);
                        sb.append("\n[... section truncated for length ...]\n");
                    } else {
                        sb.append("[Section content omitted due to length constraints]\n");
                    }
                } else {
                    sb.append(content);
                    sb.append("\n");
                }
            }

            if (!section.getCodeSnippets().isEmpty()) {
                sb.append("\nCode snippets in this section:\n");
                for (int j = 0; j < section.getCodeSnippets().size(); j++) {
                    String snippet = section.getCodeSnippets().get(j);
                    String truncated = snippet.length() > 500
                            ? snippet.substring(0, 500) + "\n// ... truncated ..."
                            : snippet;
                    sb.append(String.format("```\n%s\n```\n", truncated));
                }
            }

            sb.append("\n---\n\n");

            if (sb.length() > MAX_ROUND_CHARS) {
                sb.append(String.format("[Remaining sections omitted. %d of %d sections shown.]\n",
                        roundSpec.sectionIndices().indexOf(idx) + 1, roundSpec.sectionIndices().size()));
                break;
            }
        }

        sb.append("\n## Instructions\n");
        sb.append("Please evaluate the above document sections according to the rubric provided. ");
        sb.append("Focus on these criteria: ").append(String.join(", ", roundSpec.criteriaKeys())).append(". ");
        sb.append("Respond with ONLY the JSON object as specified. Do not include any markdown code fences ");
        sb.append("or additional text outside the JSON.\n");

        log.info("Built round user prompt: {} chars for round {} ({} sections)",
                sb.length(), roundSpec.roundNumber(), roundSpec.sectionIndices().size());
        return sb.toString();
    }

    public String buildSynthesisPrompt(List<RoundOutput> priorRoundOutputs, List<RulePackageItem> enabledRules) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are synthesizing evaluation results from multiple assessment rounds into a final score.\n\n");
        sb.append("## Prior Round Results\n\n");

        for (RoundOutput output : priorRoundOutputs) {
            if (!output.success()) {
                sb.append(String.format("### Round %d: FAILED (%s)\n\n",
                        output.roundNumber(), output.errorMessage()));
                continue;
            }
            sb.append(String.format("### Round %d Results:\n```json\n%s\n```\n\n",
                    output.roundNumber(), output.rawResponse()));
        }

        sb.append("## Criteria Weights\n");
        for (RulePackageItem item : enabledRules) {
            if (Boolean.TRUE.equals(item.getEnabled())) {
                sb.append(String.format("- %s (key: %s): weight %.1f\n",
                        item.getRule().getName(), item.getRule().getRuleKey(), item.getWeight()));
            }
        }

        sb.append("\n## Instructions\n");
        sb.append("Synthesize the above round results into a final evaluation. ");
        sb.append("Reconcile any differences between rounds. ");
        sb.append("Combine evidence from all rounds, keeping section references intact. ");
        sb.append("If rounds disagree on a criterion score, consider the evidence quality and set confidence accordingly. ");
        sb.append("Produce the final JSON output with the same format as individual rounds.\n");

        log.info("Built synthesis prompt: {} chars from {} rounds", sb.length(), priorRoundOutputs.size());
        return sb.toString();
    }

    public String previewSystemPrompt(LlmConfig config, List<RulePackageItem> enabledRules) {
        return buildSystemPrompt(config, enabledRules);
    }

    private String buildCriteriaBlock(List<RulePackageItem> enabledRules) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (RulePackageItem item : enabledRules) {
            if (!Boolean.TRUE.equals(item.getEnabled())) continue;

            String criterionPrompt = item.getRule().getLlmCriterionPrompt();
            if (criterionPrompt == null || criterionPrompt.isBlank()) {
                criterionPrompt = buildFallbackCriterionPrompt(item);
            }

            sb.append(String.format("\n### Criterion %d: %s (key: %s, weight: %.1f)\n",
                    index++, item.getRule().getName(), item.getRule().getRuleKey(), item.getWeight()));
            sb.append(criterionPrompt);
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildFallbackCriterionPrompt(RulePackageItem item) {
        String desc = item.getRule().getDescription();
        if (desc == null || desc.isBlank()) {
            desc = "Evaluate this criterion based on the quality and completeness of the content.";
        }
        return String.format("""
                %s

                - EXCELLENT (30): Outstanding demonstration of this criterion.
                - PROFICIENT (24): Solid, thorough work with minor gaps.
                - COMPETENT (18): Adequate work with some gaps.
                - DEVELOPING (12): Basic work with significant gaps.
                - INADEQUATE (6): Poor or missing work.
                """, desc);
    }

    private String buildDefaultOutputFormat(List<RulePackageItem> enabledRules) {
        String criteriaKeys = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(item -> "\"" + item.getRule().getRuleKey() + "\"")
                .collect(Collectors.joining(", "));

        return DEFAULT_OUTPUT_FORMAT.replace("<criterion_rule_key>",
                enabledRules.stream()
                        .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                        .map(item -> item.getRule().getRuleKey())
                        .findFirst().orElse("criterion_key"));
    }
}
