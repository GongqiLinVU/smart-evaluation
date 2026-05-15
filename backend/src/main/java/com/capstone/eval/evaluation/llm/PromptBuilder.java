package com.capstone.eval.evaluation.llm;

import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds system and user prompts for LLM-based capstone document evaluation.
 * The prompts encode the rubric, scoring levels, and required JSON output format
 * so the LLM produces structured, parseable responses.
 */
@Component
@Slf4j
public class PromptBuilder {

    private static final int MAX_USER_PROMPT_CHARS = 60_000;

    /**
     * Build the system prompt that instructs the LLM to act as an IT capstone assessor.
     * Includes the full rubric definition, scoring levels, and required JSON output format.
     */
    public String buildSystemPrompt() {
        return """
                You are an experienced IT capstone project assessor. Your task is to evaluate \
                a student's final project report based on a specific rubric. You must be fair, \
                consistent, and evidence-based in your assessment.

                ## Rubric

                The report is evaluated on three criteria. Each criterion is scored at one of \
                five performance levels:

                | Level       | Points | Description                          |
                |-------------|--------|--------------------------------------|
                | EXCELLENT   | 30     | Outstanding, comprehensive work      |
                | PROFICIENT  | 24     | Solid, thorough work with minor gaps |
                | COMPETENT   | 18     | Adequate work with some gaps         |
                | DEVELOPING  | 12     | Basic work with significant gaps     |
                | INADEQUATE  | 6      | Poor or missing work                 |

                ### Criterion 1: Logic Explanation
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

                ### Criterion 2: Methodology
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

                ### Criterion 3: Implementation Detail
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

                ## Scoring Rules
                - The overall score is the AVERAGE of the three criterion scores (rounded to the nearest \
                valid level score).
                - The overall level corresponds to the overall score.
                - You MUST pick one of the five valid score values: 30, 24, 18, 12, or 6 for each criterion.
                - Provide specific evidence from the document to justify each score.
                - Be objective and base your assessment only on what is written in the document.

                ## Output Format
                You MUST respond with ONLY a JSON object in the following format (no markdown fencing, \
                no additional text before or after):
                {
                  "overall_score": <integer: average of criterion scores, rounded to nearest valid level>,
                  "overall_level": "<EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE>",
                  "criteria": {
                    "logic_explanation": {
                      "score": <6|12|18|24|30>,
                      "level": "<EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE>",
                      "justification": "<2-4 sentences explaining the score>",
                      "evidence": ["<quote or reference from document>", "..."]
                    },
                    "methodology": {
                      "score": <6|12|18|24|30>,
                      "level": "<EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE>",
                      "justification": "<2-4 sentences explaining the score>",
                      "evidence": ["<quote or reference from document>", "..."]
                    },
                    "implementation_detail": {
                      "score": <6|12|18|24|30>,
                      "level": "<EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE>",
                      "justification": "<2-4 sentences explaining the score>",
                      "evidence": ["<quote or reference from document>", "..."]
                    }
                  },
                  "strengths": ["<strength 1>", "<strength 2>", "..."],
                  "improvements": ["<improvement 1>", "<improvement 2>", "..."],
                  "overall_feedback": "<2-4 sentence summary of the overall assessment>"
                }
                """;
    }

    /**
     * Build the user prompt containing the document content for evaluation.
     * Includes document overview, each section's content, and code snippets.
     * Truncates to ~60,000 characters to fit within typical context windows.
     *
     * @param document the parsed student document
     * @return the user prompt string
     */
    public String buildUserPrompt(ParsedDocument document) {
        StringBuilder sb = new StringBuilder();

        // 1. Document overview
        sb.append("## Document Overview\n");
        sb.append(String.format("- Total word count: %d\n", document.getTotalWordCount()));
        sb.append(String.format("- Number of sections: %d\n", document.getSections().size()));
        sb.append(String.format("- Code snippets: %d\n", document.getCodeSnippetCount()));
        sb.append(String.format("- Images: %d\n", document.getImageCount()));
        sb.append(String.format("- Tables: %d\n", document.getTableCount()));
        sb.append(String.format("- Links: %d\n", document.getLinkCount()));
        sb.append(String.format("- Has executive summary: %s\n", document.isHasExecutiveSummary()));
        sb.append(String.format("- Has methodology section: %s\n", document.isHasMethodologySection()));
        sb.append(String.format("- Has implementation section: %s\n", document.isHasImplementationSection()));
        sb.append(String.format("- Has architecture section: %s\n", document.isHasArchitectureSection()));
        sb.append(String.format("- Has testing section: %s\n", document.isHasTestingSection()));
        sb.append(String.format("- Has conclusion section: %s\n", document.isHasConclusionSection()));
        sb.append("\n");

        // 2. Document sections with content
        sb.append("## Document Sections\n\n");

        List<DocumentSection> sections = document.getSections();
        for (int i = 0; i < sections.size(); i++) {
            DocumentSection section = sections.get(i);
            String headingPrefix = "#".repeat(Math.min(section.getHeadingLevel(), 6));

            sb.append(String.format("### Section %d: %s %s\n", i + 1, headingPrefix, section.getHeading()));
            sb.append(String.format("Word count: %d | Images: %d | Tables: %d | Code snippets: %d\n",
                    section.getWordCount(), section.getImageCount(),
                    section.getTableCount(), section.getCodeSnippets().size()));

            if (section.getTechnicalVocabularyDensity() > 0) {
                sb.append(String.format("Technical vocabulary density: %.2f\n",
                        section.getTechnicalVocabularyDensity()));
            }

            sb.append("\n");

            // Add section content, truncating if needed
            String content = section.getContent();
            if (content != null && !content.isBlank()) {
                if (sb.length() + content.length() > MAX_USER_PROMPT_CHARS - 2000) {
                    // Truncate this section to fit within budget, reserving space for remaining sections
                    int remaining = MAX_USER_PROMPT_CHARS - sb.length() - 2000;
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

            // Add code snippets from this section
            if (!section.getCodeSnippets().isEmpty()) {
                sb.append("\nCode snippets in this section:\n");
                for (int j = 0; j < section.getCodeSnippets().size(); j++) {
                    String snippet = section.getCodeSnippets().get(j);
                    sb.append(String.format("```\n%s\n```\n", truncateSnippet(snippet, 500)));
                }
            }

            sb.append("\n---\n\n");

            // Check total length and break if we've exceeded the budget
            if (sb.length() > MAX_USER_PROMPT_CHARS) {
                sb.append("[Remaining sections omitted due to length constraints. ");
                sb.append(String.format("%d of %d sections shown.]\n", i + 1, sections.size()));
                break;
            }
        }

        // 3. Instruction reminder
        sb.append("\n## Instructions\n");
        sb.append("Please evaluate the above document according to the rubric provided in the system prompt. ");
        sb.append("Respond with ONLY the JSON object as specified. Do not include any markdown code fences ");
        sb.append("or additional text outside the JSON.\n");

        String prompt = sb.toString();
        log.info("Built user prompt: {} chars, {} sections included",
                prompt.length(), document.getSections().size());

        return prompt;
    }

    /**
     * Truncate a code snippet to a maximum length, appending an ellipsis if truncated.
     */
    private String truncateSnippet(String snippet, int maxLength) {
        if (snippet == null) return "";
        if (snippet.length() <= maxLength) return snippet;
        return snippet.substring(0, maxLength) + "\n// ... truncated ...";
    }
}
