package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule that assesses how well the student explains the logic and reasoning
 * behind their implementation choices.
 *
 * <p>Six weighted signals are aggregated into a 0-100 raw score which is then
 * mapped to a {@link PerformanceLevel}.</p>
 */
@Component
public class LogicExplanationRule {

    private static final String CRITERION_NAME = "Logic & Explanation";

    // Heading keywords that indicate a section discusses module/logic detail
    private static final Set<String> LOGIC_HEADING_KEYWORDS = Set.of(
            "implementation", "detailed", "function", "module",
            "component", "logic", "how", "system"
    );

    // Keywords indicating data-flow discussion
    private static final List<String> DATA_FLOW_KEYWORDS = List.of(
            "flow", "sequence", "passes", "returns", "calls", "sends",
            "receives", "triggers", "routes", "forwards", "processes", "transforms"
    );

    // Keywords indicating decision rationale
    private static final List<String> RATIONALE_KEYWORDS = List.of(
            "because", "reason", "why", "decided", "chose", "alternative",
            "trade-off", "instead of", "compared", "advantage", "benefit"
    );

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public CriterionResult evaluate(ParsedDocument doc) {
        List<DocumentSection> logicSections = findLogicSections(doc);
        String fullText = buildFullText(doc);

        // 1. Section coverage (20%)
        int logicSectionCount = countSubstantialLogicSections(logicSections);
        double sectionCoverageScore = scoreSectionCoverage(logicSectionCount);

        // 2. Explanation depth (20%)
        double avgWordCount = averageWordCount(logicSections);
        double explanationDepthScore = scoreExplanationDepth(avgWordCount);

        // 3. Technical vocabulary density (15%)
        double avgDensity = averageTechDensity(logicSections);
        double techDensityScore = scoreTechDensity(avgDensity);

        // 4. Data flow indicators (15%)
        int flowCount = countKeywords(fullText, DATA_FLOW_KEYWORDS);
        double dataFlowScore = scoreDataFlow(flowCount);

        // 5. Decision rationale indicators (15%)
        int rationaleCount = countKeywords(fullText, RATIONALE_KEYWORDS);
        double rationaleScore = scoreRationale(rationaleCount);

        // 6. Diagram support (15%)
        int diagramCount = countDiagramsInLogicSections(logicSections);
        double diagramScore = scoreDiagrams(diagramCount);

        // Weighted aggregate
        double rawScore = sectionCoverageScore * 0.20
                + explanationDepthScore * 0.20
                + techDensityScore * 0.15
                + dataFlowScore * 0.15
                + rationaleScore * 0.15
                + diagramScore * 0.15;

        PerformanceLevel level = PerformanceLevel.fromRawScore(rawScore);

        // Sub-scores
        Map<String, CriterionResult.SubScore> subScores = new LinkedHashMap<>();
        subScores.put("section_coverage", CriterionResult.SubScore.builder()
                .score(sectionCoverageScore).max(100)
                .note(logicSectionCount + " logic section(s) with >100 words").build());
        subScores.put("explanation_depth", CriterionResult.SubScore.builder()
                .score(explanationDepthScore).max(100)
                .note("Avg " + Math.round(avgWordCount) + " words per logic section").build());
        subScores.put("technical_vocabulary", CriterionResult.SubScore.builder()
                .score(techDensityScore).max(100)
                .note(String.format("Avg density %.4f across logic sections", avgDensity)).build());
        subScores.put("data_flow_indicators", CriterionResult.SubScore.builder()
                .score(dataFlowScore).max(100)
                .note(flowCount + " data-flow keyword(s) found").build());
        subScores.put("decision_rationale", CriterionResult.SubScore.builder()
                .score(rationaleScore).max(100)
                .note(rationaleCount + " rationale keyword(s) found").build());
        subScores.put("diagram_support", CriterionResult.SubScore.builder()
                .score(diagramScore).max(100)
                .note(diagramCount + " image(s) in logic/architecture sections").build());

        // Evidence
        List<String> evidence = buildEvidence(logicSections, flowCount, rationaleCount, diagramCount);

        // Justification
        String justification = buildJustification(
                logicSectionCount, avgWordCount, avgDensity,
                flowCount, rationaleCount, diagramCount, rawScore, level);

        return CriterionResult.builder()
                .criterionName(CRITERION_NAME)
                .rawScore(rawScore)
                .level(level)
                .points(level.getPoints())
                .justification(justification)
                .evidence(evidence)
                .subScores(subScores)
                .build();
    }

    // -------------------------------------------------------------------
    // Section helpers
    // -------------------------------------------------------------------

    private List<DocumentSection> findLogicSections(ParsedDocument doc) {
        return doc.getSections().stream()
                .filter(s -> {
                    if (s.getHeading() == null) return false;
                    String h = s.getHeading().toLowerCase();
                    return LOGIC_HEADING_KEYWORDS.stream().anyMatch(h::contains);
                })
                .collect(Collectors.toList());
    }

    private int countSubstantialLogicSections(List<DocumentSection> logicSections) {
        return (int) logicSections.stream()
                .filter(s -> s.getWordCount() > 100)
                .count();
    }

    private double averageWordCount(List<DocumentSection> sections) {
        if (sections.isEmpty()) return 0;
        return sections.stream().mapToInt(DocumentSection::getWordCount).average().orElse(0);
    }

    private double averageTechDensity(List<DocumentSection> sections) {
        if (sections.isEmpty()) return 0;
        return sections.stream()
                .mapToDouble(DocumentSection::getTechnicalVocabularyDensity)
                .average().orElse(0);
    }

    private int countDiagramsInLogicSections(List<DocumentSection> logicSections) {
        return logicSections.stream().mapToInt(DocumentSection::getImageCount).sum();
    }

    private String buildFullText(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (DocumentSection s : doc.getSections()) {
            if (s.getContent() != null) {
                sb.append(s.getContent().toLowerCase()).append(' ');
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------
    // Keyword counting (case-insensitive, already lowered text)
    // -------------------------------------------------------------------

    private int countKeywords(String text, List<String> keywords) {
        int total = 0;
        for (String kw : keywords) {
            int idx = 0;
            String target = kw.toLowerCase();
            while ((idx = text.indexOf(target, idx)) != -1) {
                total++;
                idx += target.length();
            }
        }
        return total;
    }

    // -------------------------------------------------------------------
    // Scoring functions
    // -------------------------------------------------------------------

    private double scoreSectionCoverage(int count) {
        if (count == 0) return 0;
        if (count <= 2) return 40;
        if (count <= 4) return 60;
        if (count <= 6) return 80;
        return 100;
    }

    private double scoreExplanationDepth(double avgWords) {
        if (avgWords < 50) return 20;
        if (avgWords < 100) return 40;
        if (avgWords < 200) return 60;
        if (avgWords < 400) return 80;
        return 100;
    }

    private double scoreTechDensity(double density) {
        if (density < 0.02) return 20;
        if (density < 0.04) return 40;
        if (density < 0.06) return 60;
        if (density < 0.08) return 80;
        return 100;
    }

    private double scoreDataFlow(int count) {
        if (count == 0) return 0;
        if (count <= 3) return 40;
        if (count <= 6) return 60;
        if (count <= 10) return 80;
        return 100;
    }

    private double scoreRationale(int count) {
        if (count == 0) return 0;
        if (count <= 3) return 40;
        if (count <= 6) return 60;
        if (count <= 10) return 80;
        return 100;
    }

    private double scoreDiagrams(int count) {
        if (count == 0) return 20;
        if (count == 1) return 50;
        if (count <= 3) return 70;
        if (count <= 5) return 85;
        return 100;
    }

    // -------------------------------------------------------------------
    // Evidence & Justification
    // -------------------------------------------------------------------

    private List<String> buildEvidence(List<DocumentSection> logicSections,
                                       int flowCount, int rationaleCount, int diagramCount) {
        List<String> evidence = new ArrayList<>();

        List<String> sectionNames = logicSections.stream()
                .map(DocumentSection::getHeading)
                .collect(Collectors.toList());
        if (!sectionNames.isEmpty()) {
            evidence.add("Logic-related sections found: " + String.join(", ", sectionNames));
        }

        int substantial = countSubstantialLogicSections(logicSections);
        evidence.add(substantial + " of " + logicSections.size()
                + " logic sections contain more than 100 words");

        if (flowCount > 0) {
            evidence.add(flowCount + " data-flow keyword occurrences detected across the document");
        }
        if (rationaleCount > 0) {
            evidence.add(rationaleCount + " decision-rationale keyword occurrences detected");
        }
        if (diagramCount > 0) {
            evidence.add(diagramCount + " diagram(s)/image(s) found in logic sections");
        }
        return evidence;
    }

    private String buildJustification(int sectionCount, double avgWords, double avgDensity,
                                      int flowCount, int rationaleCount, int diagramCount,
                                      double rawScore, PerformanceLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("The document was evaluated on its explanation of system logic and design reasoning. ");

        if (sectionCount == 0) {
            sb.append("No dedicated logic/implementation sections with substantial content were found, ");
            sb.append("which significantly limits the explanation quality. ");
        } else {
            sb.append(String.format("Found %d section(s) with substantive logic discussion " +
                    "(average %.0f words each). ", sectionCount, avgWords));
        }

        if (avgDensity >= 0.06) {
            sb.append("Technical vocabulary usage is strong. ");
        } else if (avgDensity >= 0.03) {
            sb.append("Technical vocabulary usage is moderate. ");
        } else {
            sb.append("Technical vocabulary usage is limited. ");
        }

        if (flowCount >= 7) {
            sb.append("Data-flow language is well-represented. ");
        } else if (flowCount >= 4) {
            sb.append("Some data-flow language is present. ");
        } else {
            sb.append("Data-flow language is minimal or absent. ");
        }

        if (rationaleCount >= 7) {
            sb.append("Decision rationale is clearly articulated throughout the document. ");
        } else if (rationaleCount >= 4) {
            sb.append("Some decision rationale is provided. ");
        } else {
            sb.append("Decision rationale is largely missing. ");
        }

        if (diagramCount >= 2) {
            sb.append("Diagrams support the explanations effectively. ");
        } else if (diagramCount == 1) {
            sb.append("One diagram is present but more visual support would strengthen the report. ");
        } else {
            sb.append("No diagrams were found in the logic sections. ");
        }

        sb.append(String.format("Overall raw score: %.1f/100 (%s, %d points).",
                rawScore, level.name(), level.getPoints()));

        return sb.toString();
    }
}
