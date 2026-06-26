package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.model.CriterionScore;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a compact evidence digest from the parsed document, guided by
 * the original evaluation's citations. This gives verification rounds
 * enough context to verify scores WITHOUT sending the full 30+ page document.
 *
 * Algorithm:
 * 1. Parse the original evaluation's evidence citations (sectionIndex, quotes)
 * 2. Extract bounded excerpts (~500 words) around each cited area
 * 3. Identify "blind spot" sections (high criterion affinity but never cited)
 * 4. Include 1 blind-spot excerpt per criterion (~300 words) as a check
 * 5. Budget enforcement: prioritize low-confidence criteria, truncate high-confidence first
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceDigestBuilder {

    private final ObjectMapper objectMapper;

    private static final int MAX_DIGEST_CHARS = 25_000;
    private static final int EXCERPT_WORDS_PER_CITATION = 500;
    private static final int BLIND_SPOT_WORDS = 300;
    private static final int MIN_EXCERPT_WORDS = 100;

    private static final Map<String, List<String>> CRITERION_SECTION_KEYWORDS = Map.of(
            "methodology", List.of("methodology", "approach", "design", "process", "framework",
                    "architecture", "planning", "strategy", "requirement"),
            "implementation_detail", List.of("implementation", "code", "development", "function",
                    "module", "component", "deploy", "database", "configuration"),
            "logic_explanation", List.of("logic", "explanation", "reasoning", "algorithm",
                    "flow", "decision", "analysis", "overview", "system", "how", "result", "conclusion")
    );

    /**
     * Build a full evidence digest for Round 1 (candidate generation).
     * Includes cited excerpts + blind spots + document structure overview.
     */
    public String buildFullDigest(
            EvaluationResult originalResult,
            ParsedDocument document,
            List<RulePackageItem> enabledRules
    ) {
        StringBuilder digest = new StringBuilder();

        // 1. Document structure overview (compact)
        digest.append(buildStructureOverview(document));

        // 2. Parse citations from the original evaluation
        Map<String, List<CitedEvidence>> citationMap = extractCitations(originalResult);

        // 3. Collect cited section indices
        Set<Integer> citedSectionIndices = citationMap.values().stream()
                .flatMap(List::stream)
                .map(c -> c.sectionIndex)
                .filter(idx -> idx >= 0 && idx < document.getSections().size())
                .collect(Collectors.toSet());

        // 4. Build excerpts for cited sections, grouped by criterion
        Map<String, Integer> criterionConfidence = buildConfidenceMap(originalResult);
        List<String> criteriaByPriority = prioritizeCriteria(criterionConfidence);

        int budgetRemaining = MAX_DIGEST_CHARS - digest.length();
        Map<String, String> criterionExcerpts = new LinkedHashMap<>();

        for (String criterionKey : criteriaByPriority) {
            List<CitedEvidence> citations = citationMap.getOrDefault(criterionKey, List.of());
            String excerpt = buildCitedExcerpts(citations, document, budgetRemaining / criteriaByPriority.size());
            criterionExcerpts.put(criterionKey, excerpt);
            budgetRemaining -= excerpt.length();
        }

        // 5. Identify blind spots and add excerpts
        Map<String, List<Integer>> blindSpots = findBlindSpots(
                document, enabledRules, citedSectionIndices);

        Map<String, String> blindSpotExcerpts = new LinkedHashMap<>();
        int blindSpotBudget = Math.min(budgetRemaining / 2, 6000);
        for (Map.Entry<String, List<Integer>> entry : blindSpots.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            int sectionIdx = entry.getValue().get(0);
            String excerpt = extractBoundedExcerpt(
                    document.getSections().get(sectionIdx), BLIND_SPOT_WORDS);
            if (excerpt.length() <= blindSpotBudget) {
                blindSpotExcerpts.put(entry.getKey(), excerpt);
                blindSpotBudget -= excerpt.length();
            }
        }

        // 6. Assemble the digest
        digest.append("\n## Cited Evidence (from original evaluation)\n\n");
        for (Map.Entry<String, String> entry : criterionExcerpts.entrySet()) {
            if (entry.getValue().isBlank()) continue;
            digest.append("### Evidence for: ").append(entry.getKey()).append("\n");
            digest.append(entry.getValue()).append("\n\n");
        }

        if (!blindSpotExcerpts.isEmpty()) {
            digest.append("## Uncited Sections (potential blind spots)\n");
            digest.append("These sections seem relevant but were NOT cited in the original evaluation. ");
            digest.append("Check if they contain evidence that changes the assessment.\n\n");
            for (Map.Entry<String, String> entry : blindSpotExcerpts.entrySet()) {
                digest.append("### Blind spot for: ").append(entry.getKey()).append("\n");
                digest.append(entry.getValue()).append("\n\n");
            }
        }

        String result = digest.toString();
        log.info("Built evidence digest: {} chars (budget: {}), {} cited sections, {} blind spots",
                result.length(), MAX_DIGEST_CHARS, citedSectionIndices.size(), blindSpotExcerpts.size());
        return result;
    }

    /**
     * Build a targeted digest for Round 3 — only includes evidence for ambiguous criteria.
     */
    public String buildTargetedDigest(
            EvaluationResult originalResult,
            ParsedDocument document,
            List<String> ambiguousCriteria
    ) {
        if (ambiguousCriteria == null || ambiguousCriteria.isEmpty()) {
            return "";
        }

        StringBuilder digest = new StringBuilder();
        digest.append("## Targeted Evidence for Ambiguous Criteria\n\n");

        Map<String, List<CitedEvidence>> citationMap = extractCitations(originalResult);
        int budgetPerCriterion = MAX_DIGEST_CHARS / ambiguousCriteria.size();

        for (String criterion : ambiguousCriteria) {
            List<CitedEvidence> citations = citationMap.getOrDefault(criterion, List.of());
            digest.append("### ").append(criterion).append("\n");

            if (!citations.isEmpty()) {
                digest.append(buildCitedExcerpts(citations, document, budgetPerCriterion));
            } else {
                List<Integer> relevantSections = findRelevantSections(document, criterion);
                if (!relevantSections.isEmpty()) {
                    int sectionIdx = relevantSections.get(0);
                    DocumentSection section = document.getSections().get(sectionIdx);
                    digest.append("(No direct citation found — showing most relevant section)\n");
                    digest.append("Section: ").append(section.getHeading()).append("\n");
                    digest.append(extractBoundedExcerpt(section, EXCERPT_WORDS_PER_CITATION));
                }
            }
            digest.append("\n\n");
        }

        String result = digest.toString();
        log.info("Built targeted digest for {} ambiguous criteria: {} chars",
                ambiguousCriteria.size(), result.length());
        return result;
    }

    private String buildStructureOverview(ParsedDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Document Structure Overview\n");
        sb.append(String.format("Total words: %d | Sections: %d | Images: %d | Tables: %d | Code snippets: %d\n\n",
                document.getTotalWordCount(),
                document.getSections().size(),
                document.getImageCount(),
                document.getTableCount(),
                document.getCodeSnippetCount()));

        sb.append("Sections:\n");
        List<DocumentSection> sections = document.getSections();
        for (int i = 0; i < sections.size(); i++) {
            DocumentSection s = sections.get(i);
            sb.append(String.format("  %d. %s (%d words", i, s.getHeading(), s.getWordCount()));
            if (s.getImageCount() > 0) sb.append(", ").append(s.getImageCount()).append(" imgs");
            if (s.getTableCount() > 0) sb.append(", ").append(s.getTableCount()).append(" tables");
            if (!s.getCodeSnippets().isEmpty()) sb.append(", ").append(s.getCodeSnippets().size()).append(" code");
            sb.append(")\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Parse the original evaluation result to find cited evidence per criterion.
     * Works from both rawLlmResponse JSON and the justification text.
     */
    private Map<String, List<CitedEvidence>> extractCitations(EvaluationResult result) {
        Map<String, List<CitedEvidence>> map = new LinkedHashMap<>();

        // Try parsing from rawLlmResponse (contains sectionIndex, sectionName, quote)
        String raw = result.getRawLlmResponse();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(extractJsonBlock(raw));
                JsonNode criteria = root.get("criteria");
                if (criteria != null && criteria.isObject()) {
                    var fields = criteria.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        String key = entry.getKey();
                        JsonNode criterion = entry.getValue();
                        List<CitedEvidence> citations = new ArrayList<>();

                        JsonNode evidence = criterion.get("evidence");
                        if (evidence != null && evidence.isArray()) {
                            for (JsonNode ev : evidence) {
                                int sectionIndex = ev.has("sectionIndex") ? ev.get("sectionIndex").asInt(-1) : -1;
                                String sectionName = ev.has("sectionName") ? ev.get("sectionName").asText("") : "";
                                String quote = ev.has("quote") ? ev.get("quote").asText("") : "";
                                String sentiment = ev.has("sentiment") ? ev.get("sentiment").asText("") : "";
                                citations.add(new CitedEvidence(sectionIndex, sectionName, quote, sentiment));
                            }
                        }

                        // Fallback: use sectionIndex from justification if no evidence array
                        if (citations.isEmpty()) {
                            int sectionIndex = criterion.has("sectionIndex")
                                    ? criterion.get("sectionIndex").asInt(-1) : -1;
                            if (sectionIndex >= 0) {
                                citations.add(new CitedEvidence(sectionIndex, "", "", ""));
                            }
                        }

                        map.put(key, citations);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not parse rawLlmResponse for citations: {}", e.getMessage());
            }
        }

        // Fallback: extract from CriterionScore justifications (section name references)
        if (map.isEmpty() && result.getCriterionScores() != null) {
            for (CriterionScore cs : result.getCriterionScores()) {
                map.put(cs.getCriterionName(), List.of());
            }
        }

        return map;
    }

    private String buildCitedExcerpts(
            List<CitedEvidence> citations,
            ParsedDocument document,
            int charBudget
    ) {
        if (citations.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int budgetPerCitation = Math.max(200, charBudget / Math.max(1, citations.size()));

        for (CitedEvidence citation : citations) {
            int idx = citation.sectionIndex;
            if (idx < 0 || idx >= document.getSections().size()) continue;

            DocumentSection section = document.getSections().get(idx);
            sb.append(String.format("**Section %d: %s** (words: %d)\n",
                    idx, section.getHeading(), section.getWordCount()));

            // If we have a quote, show context around it
            if (citation.quote != null && !citation.quote.isBlank()) {
                String contextAroundQuote = extractContextAroundQuote(
                        section.getContent(), citation.quote, budgetPerCitation);
                sb.append(contextAroundQuote).append("\n");
            } else {
                // No quote — show bounded excerpt from the section
                int wordLimit = Math.min(EXCERPT_WORDS_PER_CITATION,
                        budgetPerCitation / 6); // ~6 chars per word
                sb.append(extractBoundedExcerpt(section, wordLimit)).append("\n");
            }

            if (sb.length() > charBudget) {
                sb.append("[... budget exceeded, remaining citations omitted ...]\n");
                break;
            }
        }

        return sb.toString();
    }

    /**
     * Extract context around a quoted passage in the section content.
     * Returns ~200 words before and after the quote location.
     */
    private String extractContextAroundQuote(String content, String quote, int charBudget) {
        if (content == null || content.isBlank()) return "[section content unavailable]";

        String lowerContent = content.toLowerCase();
        String lowerQuote = quote.toLowerCase();

        // Find the quote (or partial match) in the content
        int quoteStart = lowerContent.indexOf(lowerQuote);
        if (quoteStart < 0) {
            // Try matching first 30 chars of the quote for fuzzy match
            String partial = lowerQuote.length() > 30 ? lowerQuote.substring(0, 30) : lowerQuote;
            quoteStart = lowerContent.indexOf(partial);
        }

        if (quoteStart < 0) {
            // Quote not found — return beginning of section
            return truncateToWords(content, EXCERPT_WORDS_PER_CITATION);
        }

        // Extract window: 500 chars before, quote, 500 chars after
        int windowBefore = Math.min(500, quoteStart);
        int windowAfter = Math.min(500, content.length() - quoteStart - quote.length());
        int start = quoteStart - windowBefore;
        int end = Math.min(content.length(), quoteStart + quote.length() + windowAfter);

        String excerpt = content.substring(start, end);
        if (start > 0) excerpt = "..." + excerpt;
        if (end < content.length()) excerpt = excerpt + "...";

        if (excerpt.length() > charBudget) {
            excerpt = excerpt.substring(0, charBudget) + "...";
        }

        return excerpt;
    }

    private String extractBoundedExcerpt(DocumentSection section, int wordLimit) {
        String content = section.getContent();
        if (content == null || content.isBlank()) return "[no text content]";
        return truncateToWords(content, wordLimit);
    }

    private String truncateToWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        sb.append(" [... ").append(words.length - maxWords).append(" more words ...]");
        return sb.toString();
    }

    /**
     * Find sections relevant to a criterion that were NOT cited.
     * These are "blind spots" — evidence the original evaluation may have missed.
     */
    private Map<String, List<Integer>> findBlindSpots(
            ParsedDocument document,
            List<RulePackageItem> enabledRules,
            Set<Integer> citedIndices
    ) {
        Map<String, List<Integer>> blindSpots = new LinkedHashMap<>();

        for (RulePackageItem item : enabledRules) {
            if (!Boolean.TRUE.equals(item.getEnabled())) continue;
            String ruleKey = item.getRule().getRuleKey();

            List<Integer> relevant = findRelevantSections(document, ruleKey);
            List<Integer> uncited = relevant.stream()
                    .filter(idx -> !citedIndices.contains(idx))
                    .toList();

            blindSpots.put(ruleKey, uncited);
        }

        return blindSpots;
    }

    private List<Integer> findRelevantSections(ParsedDocument document, String criterionKey) {
        List<String> keywords = findKeywordsForCriterion(criterionKey);
        List<Integer> relevant = new ArrayList<>();

        List<DocumentSection> sections = document.getSections();
        for (int i = 0; i < sections.size(); i++) {
            String heading = sections.get(i).getHeading().toLowerCase();
            for (String kw : keywords) {
                if (heading.contains(kw)) {
                    relevant.add(i);
                    break;
                }
            }
        }

        return relevant;
    }

    private List<String> findKeywordsForCriterion(String criterionKey) {
        String keyLower = criterionKey.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CRITERION_SECTION_KEYWORDS.entrySet()) {
            if (keyLower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // Fallback: generic keywords from the criterion key itself
        return List.of(keyLower.replace("_", " ").split("\\s+"));
    }

    private Map<String, Integer> buildConfidenceMap(EvaluationResult result) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (result.getCriterionScores() != null) {
            for (CriterionScore cs : result.getCriterionScores()) {
                double conf = cs.getConfidence() != null ? cs.getConfidence() : 0.5;
                map.put(cs.getCriterionName(), (int) (conf * 100));
            }
        }
        return map;
    }

    /**
     * Prioritize criteria for budget allocation: low confidence first (needs more evidence).
     */
    private List<String> prioritizeCriteria(Map<String, Integer> confidenceMap) {
        return confidenceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String extractJsonBlock(String raw) {
        String trimmed = raw.trim();
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

    private record CitedEvidence(int sectionIndex, String sectionName, String quote, String sentiment) {}
}
