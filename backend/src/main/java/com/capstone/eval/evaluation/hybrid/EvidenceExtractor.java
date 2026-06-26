package com.capstone.eval.evaluation.hybrid;

import com.capstone.eval.evaluation.hybrid.model.*;
import com.capstone.eval.evaluation.llm.*;
import com.capstone.eval.exception.EvaluationException;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.Rule;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceExtractor {

    private static final int MAX_DOCUMENT_CHARS = 50_000;

    private final LlmProviderFactory providerFactory;
    private final ObjectMapper objectMapper;

    public EvidenceReport extract(ParsedDocument document, RulePackageItem item, LlmConfig llmConfig) {
        Rule rule = item.getRule();
        EvidenceQuestionSet questionSet = deserializeQuestions(item);

        if (questionSet == null || questionSet.getQuestions() == null || questionSet.getQuestions().isEmpty()) {
            throw new EvaluationException(
                    "No evidence questions configured for rule: " + rule.getRuleKey());
        }

        String systemPrompt = buildSystemPrompt(rule, questionSet);
        String userPrompt = buildDocumentPrompt(document);

        LlmProvider provider = resolveProvider(llmConfig);
        LlmOptions options = new LlmOptions(0.0, 4096,
                llmConfig != null ? llmConfig.getModel() : null);

        log.info("Extracting evidence for criterion '{}' ({} questions)",
                rule.getRuleKey(), questionSet.getQuestions().size());

        LlmResponse response = provider.chat(
                List.of(LlmMessage.system(systemPrompt), LlmMessage.user(userPrompt)),
                options
        );

        EvidenceReport report = parseResponse(response.content(), rule.getRuleKey());
        validateCitations(report, document);

        log.info("Evidence extraction complete for '{}': {} observations, {} tokens used",
                rule.getRuleKey(),
                report.getObservations().size(),
                response.promptTokens() + response.completionTokens());

        return report;
    }

    private EvidenceQuestionSet deserializeQuestions(RulePackageItem item) {
        if (item.getEvidenceQuestions() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(item.getEvidenceQuestions(), EvidenceQuestionSet.class);
        } catch (Exception e) {
            throw new EvaluationException("Failed to parse evidence questions for item " + item.getId(), e);
        }
    }

    private LlmProvider resolveProvider(LlmConfig llmConfig) {
        if (llmConfig != null && llmConfig.getProvider() != null) {
            return providerFactory.getProvider(llmConfig.getProvider());
        }
        return providerFactory.getDefaultProvider();
    }

    private String buildSystemPrompt(Rule rule, EvidenceQuestionSet questionSet) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PREAMBLE);
        sb.append("\n\n## Criterion: ").append(rule.getName()).append("\n");
        if (rule.getDescription() != null) {
            sb.append("Description: ").append(rule.getDescription()).append("\n");
        }
        sb.append("\n## Questions to Answer\n\n");

        for (EvidenceQuestion q : questionSet.getQuestions()) {
            sb.append("- **").append(q.getId()).append("** (").append(q.getType()).append(")");
            if (q.isRequired()) sb.append(" [REQUIRED]");
            sb.append("\n  ").append(q.getText()).append("\n\n");
        }

        sb.append(OUTPUT_FORMAT);
        return sb.toString();
    }

    private String buildDocumentPrompt(ParsedDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Student Document\n\n");
        sb.append("Total word count: ").append(document.getTotalWordCount()).append("\n");
        sb.append("Sections: ").append(document.getSections().size()).append("\n\n");

        List<DocumentSection> sections = document.getSections();
        for (int i = 0; i < sections.size(); i++) {
            DocumentSection section = sections.get(i);
            sb.append("### [Section ").append(i).append("] ").append(section.getHeading()).append("\n");

            String content = section.getContent();
            if (content != null && !content.isBlank()) {
                if (sb.length() + content.length() > MAX_DOCUMENT_CHARS - 500) {
                    int remaining = MAX_DOCUMENT_CHARS - sb.length() - 500;
                    if (remaining > 200) {
                        sb.append(content, 0, remaining);
                        sb.append("\n[... truncated ...]\n");
                    }
                    break;
                }
                sb.append(content).append("\n");
            }

            if (section.getTableContents() != null && !section.getTableContents().isEmpty()) {
                for (int j = 0; j < section.getTableContents().size(); j++) {
                    String tableText = section.getTableContents().get(j);
                    if (tableText != null && !tableText.isBlank()) {
                        if (sb.length() + tableText.length() > MAX_DOCUMENT_CHARS - 500) {
                            sb.append("\n[Table ").append(j + 1).append(": truncated]\n");
                            break;
                        }
                        sb.append("\nTable ").append(j + 1).append(":\n");
                        sb.append(tableText).append("\n");
                    }
                }
            }

            if (!section.getCodeSnippets().isEmpty()) {
                for (String snippet : section.getCodeSnippets()) {
                    String truncated = snippet.length() > 300 ? snippet.substring(0, 300) + "..." : snippet;
                    sb.append("```\n").append(truncated).append("\n```\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private EvidenceReport parseResponse(String content, String criterionKey) {
        try {
            String json = extractJson(content);
            EvidenceReport report = objectMapper.readValue(json, EvidenceReport.class);
            if (report.getCriterionKey() == null) {
                report.setCriterionKey(criterionKey);
            }
            if (report.getObservations() == null) {
                report.setObservations(new ArrayList<>());
            }
            return report;
        } catch (Exception e) {
            throw new EvaluationException(
                    "Failed to parse evidence extraction response for " + criterionKey + ": " + e.getMessage(), e);
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new EvaluationException("No JSON object found in LLM evidence response");
        }
        return content.substring(start, end + 1);
    }

    private void validateCitations(EvidenceReport report, ParsedDocument document) {
        if (report.getObservations() == null) return;

        String fullText = buildFullText(document);

        for (Observation obs : report.getObservations()) {
            if (obs.getCitation() == null) continue;
            Observation.Citation cit = obs.getCitation();
            if (cit.getQuote() != null && !cit.getQuote().isBlank()) {
                boolean found = fullText.contains(cit.getQuote().trim());
                if (!found) {
                    // Try fuzzy match: check if a substantial substring exists
                    String shortQuote = cit.getQuote().trim();
                    if (shortQuote.length() > 20) {
                        shortQuote = shortQuote.substring(0, 20);
                    }
                    found = fullText.contains(shortQuote);
                }
                if (!found) {
                    log.warn("Citation not found in document for question {}: '{}'",
                            obs.getQuestionId(), cit.getQuote());
                    cit.setConfidence(0.0);
                }
            }
        }
    }

    private String buildFullText(ParsedDocument document) {
        StringBuilder sb = new StringBuilder();
        for (DocumentSection section : document.getSections()) {
            if (section.getContent() != null) {
                sb.append(section.getContent()).append(" ");
            }
            if (section.getTableContents() != null) {
                for (String table : section.getTableContents()) {
                    sb.append(table).append(" ");
                }
            }
        }
        return sb.toString();
    }

    private static final String SYSTEM_PREAMBLE = """
            You are a document evidence extractor. Your task is to read a student's capstone report \
            and answer specific factual questions about its content.

            IMPORTANT RULES:
            1. You are ONLY extracting observations — do NOT assign scores or performance levels
            2. Every answer must be supported by a direct quote (citation) from the document
            3. If you cannot find evidence for a question, set answer to null and note "no evidence found"
            4. Be conservative: only answer "true" or "yes" if there is clear evidence
            5. For LIKERT_5 questions, base your rating strictly on what's in the document

            Answer types:
            - BOOLEAN: true or false
            - TERNARY: "yes", "partial", or "no"
            - COUNT: integer >= 0
            - LIKERT_5: integer 1-5
            - TEXT: free text observation""";

    private static final String OUTPUT_FORMAT = """

            ## Output Format

            Respond with ONLY a JSON object (no markdown fences):
            {
              "criterionKey": "<the criterion key>",
              "observations": [
                {
                  "questionId": "<question ID>",
                  "answer": <value matching the question type>,
                  "citation": {
                    "sectionIndex": <integer>,
                    "sectionName": "<section heading>",
                    "quote": "<exact quote from the document supporting this answer>",
                    "confidence": <0.0 to 1.0>
                  },
                  "note": "<optional note if needed>"
                }
              ]
            }

            Include one observation per question. If no evidence exists for a question, \
            set "answer" to null, "citation" to null, and add a note explaining.""";
}
