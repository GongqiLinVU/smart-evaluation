package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule that assesses the quality of the student's methodology description,
 * including architecture design, technology justification, development process,
 * design patterns, and testing/validation.
 */
@Component
public class MethodologyRule {

    private static final String CRITERION_NAME = "Methodology";

    private static final Set<String> ARCHITECTURE_HEADING_KEYWORDS = Set.of(
            "architecture", "design", "structure", "overview", "system design",
            "high-level", "high level"
    );

    private static final List<String> TECH_NAMES = List.of(
            "streamlit", "fastapi", "sqlite", "python", "react", "spring",
            "mysql", "docker", "aws", "firebase", "flask", "django",
            "node", "express", "mongodb", "postgresql", "redis", "kubernetes",
            "angular", "vue", "typescript", "javascript", "java", "kotlin",
            "nginx", "apache", "graphql", "rest api", "swagger"
    );

    private static final List<String> JUSTIFICATION_KEYWORDS = List.of(
            "because", "chose", "suitable", "compared", "decided",
            "selected", "preferred", "opted"
    );

    private static final List<String> PROCESS_KEYWORDS = List.of(
            "agile", "sprint", "iteration", "commit", "github", "version control",
            "milestone", "weekly", "phase", "methodology", "approach",
            "scrum", "kanban", "waterfall", "ci/cd", "pipeline", "branch",
            "pull request", "code review"
    );

    private static final List<String> DESIGN_PATTERNS = List.of(
            "mvc", "factory", "singleton", "observer", "layered", "modular",
            "rest", "microservice", "repository pattern", "dependency injection",
            "separation of concerns", "failover", "fallback",
            "adapter", "decorator", "strategy pattern", "proxy",
            "event-driven", "pub-sub", "middleware"
    );

    private static final Set<String> TESTING_HEADING_KEYWORDS = Set.of(
            "test", "testing", "validation", "verification", "qa",
            "quality assurance", "evaluation"
    );

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public CriterionResult evaluate(ParsedDocument doc) {
        String fullText = buildFullText(doc);

        // 1. Architecture section presence and depth (20%)
        List<DocumentSection> archSections = findArchitectureSections(doc);
        int archWordCount = archSections.stream().mapToInt(DocumentSection::getWordCount).sum();
        double archScore = scoreArchitecture(doc.isHasArchitectureSection(), archWordCount);

        // 2. Technology justification (20%)
        int techJustPairs = countTechJustificationPairs(fullText);
        double techJustScore = scoreTechJustification(techJustPairs);

        // 3. Development process (20%)
        int processKeywordCount = countKeywords(fullText, PROCESS_KEYWORDS);
        boolean hasMethodSection = doc.isHasMethodologySection();
        double processScore = scoreDevelopmentProcess(processKeywordCount, hasMethodSection);

        // 4. Design patterns (20%)
        List<String> foundPatterns = findMatchedPatterns(fullText);
        double patternScore = scoreDesignPatterns(foundPatterns.size());

        // 5. Testing/validation (20%)
        List<DocumentSection> testSections = findTestingSections(doc);
        int testWordCount = testSections.stream().mapToInt(DocumentSection::getWordCount).sum();
        int testTables = testSections.stream().mapToInt(DocumentSection::getTableCount).sum();
        double testScore = scoreTesting(doc.isHasTestingSection(), testWordCount, testTables);

        // Weighted aggregate
        double rawScore = archScore * 0.20
                + techJustScore * 0.20
                + processScore * 0.20
                + patternScore * 0.20
                + testScore * 0.20;

        PerformanceLevel level = PerformanceLevel.fromRawScore(rawScore);

        // Sub-scores
        Map<String, CriterionResult.SubScore> subScores = new LinkedHashMap<>();
        subScores.put("architecture_section", CriterionResult.SubScore.builder()
                .score(archScore).max(100)
                .note(archWordCount + " total words in architecture section(s)").build());
        subScores.put("technology_justification", CriterionResult.SubScore.builder()
                .score(techJustScore).max(100)
                .note(techJustPairs + " technology-justification pair(s)").build());
        subScores.put("development_process", CriterionResult.SubScore.builder()
                .score(processScore).max(100)
                .note(processKeywordCount + " process keyword(s)" +
                        (hasMethodSection ? "; methodology section present" : "")).build());
        subScores.put("design_patterns", CriterionResult.SubScore.builder()
                .score(patternScore).max(100)
                .note(foundPatterns.size() + " pattern(s): " +
                        (foundPatterns.isEmpty() ? "none" : String.join(", ", foundPatterns))).build());
        subScores.put("testing_validation", CriterionResult.SubScore.builder()
                .score(testScore).max(100)
                .note(testWordCount + " words in testing section(s), " + testTables + " table(s)").build());

        // Evidence
        List<String> evidence = buildEvidence(archSections, techJustPairs,
                processKeywordCount, hasMethodSection, foundPatterns, testSections, testTables);

        // Justification
        String justification = buildJustification(archWordCount, techJustPairs,
                processKeywordCount, hasMethodSection, foundPatterns,
                doc.isHasTestingSection(), testWordCount, testTables,
                rawScore, level);

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

    private List<DocumentSection> findArchitectureSections(ParsedDocument doc) {
        return doc.getSections().stream()
                .filter(s -> {
                    if (s.getHeading() == null) return false;
                    String h = s.getHeading().toLowerCase();
                    return ARCHITECTURE_HEADING_KEYWORDS.stream().anyMatch(h::contains);
                })
                .collect(Collectors.toList());
    }

    private List<DocumentSection> findTestingSections(ParsedDocument doc) {
        return doc.getSections().stream()
                .filter(s -> {
                    if (s.getHeading() == null) return false;
                    String h = s.getHeading().toLowerCase();
                    return TESTING_HEADING_KEYWORDS.stream().anyMatch(h::contains);
                })
                .collect(Collectors.toList());
    }

    private String buildFullText(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (DocumentSection s : doc.getSections()) {
            if (s.getHeading() != null) {
                sb.append(s.getHeading().toLowerCase()).append(' ');
            }
            if (s.getContent() != null) {
                sb.append(s.getContent().toLowerCase()).append(' ');
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------
    // Keyword / pattern helpers
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

    /**
     * Count occurrences where a technology name appears within 200 characters
     * of a justification keyword.
     */
    private int countTechJustificationPairs(String text) {
        int pairs = 0;
        for (String tech : TECH_NAMES) {
            String target = tech.toLowerCase();
            int idx = 0;
            while ((idx = text.indexOf(target, idx)) != -1) {
                int windowStart = Math.max(0, idx - 200);
                int windowEnd = Math.min(text.length(), idx + target.length() + 200);
                String window = text.substring(windowStart, windowEnd);
                for (String jk : JUSTIFICATION_KEYWORDS) {
                    if (window.contains(jk.toLowerCase())) {
                        pairs++;
                        break; // count one pair per tech occurrence
                    }
                }
                idx += target.length();
            }
        }
        return pairs;
    }

    private List<String> findMatchedPatterns(String text) {
        List<String> found = new ArrayList<>();
        for (String pattern : DESIGN_PATTERNS) {
            if (text.contains(pattern.toLowerCase())) {
                found.add(pattern);
            }
        }
        return found;
    }

    // -------------------------------------------------------------------
    // Scoring functions
    // -------------------------------------------------------------------

    private double scoreArchitecture(boolean hasSection, int wordCount) {
        if (!hasSection && wordCount == 0) return 0;
        if (wordCount < 100) return 40;
        if (wordCount < 300) return 60;
        if (wordCount < 600) return 80;
        return 100;
    }

    private double scoreTechJustification(int pairs) {
        if (pairs == 0) return 20;
        if (pairs <= 2) return 50;
        if (pairs <= 4) return 70;
        return 90;
    }

    private double scoreDevelopmentProcess(int keywordCount, boolean hasMethodSection) {
        double base;
        if (keywordCount == 0) base = 10;
        else if (keywordCount <= 3) base = 30;
        else if (keywordCount <= 6) base = 50;
        else if (keywordCount <= 10) base = 70;
        else base = 90;

        // Bonus for having a dedicated methodology section
        if (hasMethodSection) {
            base = Math.min(100, base + 10);
        }
        return base;
    }

    private double scoreDesignPatterns(int count) {
        if (count == 0) return 20;
        if (count == 1) return 40;
        if (count <= 3) return 60;
        if (count <= 5) return 80;
        return 100;
    }

    private double scoreTesting(boolean hasSection, int wordCount, int tableCount) {
        if (!hasSection && wordCount == 0) return 0;
        if (wordCount < 100) return 40;
        if (wordCount < 300) return 60;
        if (wordCount >= 300 && tableCount > 0) return 100;
        if (wordCount >= 300) return 80;
        return 60;
    }

    // -------------------------------------------------------------------
    // Evidence & Justification
    // -------------------------------------------------------------------

    private List<String> buildEvidence(List<DocumentSection> archSections,
                                       int techJustPairs, int processKwCount,
                                       boolean hasMethodSection,
                                       List<String> patterns,
                                       List<DocumentSection> testSections,
                                       int testTables) {
        List<String> evidence = new ArrayList<>();

        if (!archSections.isEmpty()) {
            List<String> names = archSections.stream()
                    .map(DocumentSection::getHeading).collect(Collectors.toList());
            evidence.add("Architecture-related sections: " + String.join(", ", names));
        } else {
            evidence.add("No dedicated architecture section detected");
        }

        evidence.add(techJustPairs + " technology-justification pair(s) found (tech name near justification keyword)");

        evidence.add(processKwCount + " development process keyword(s) found"
                + (hasMethodSection ? " with a dedicated methodology section" : ""));

        if (!patterns.isEmpty()) {
            evidence.add("Design patterns referenced: " + String.join(", ", patterns));
        }

        if (!testSections.isEmpty()) {
            List<String> names = testSections.stream()
                    .map(DocumentSection::getHeading).collect(Collectors.toList());
            evidence.add("Testing sections: " + String.join(", ", names)
                    + " (" + testTables + " comparison table(s))");
        } else {
            evidence.add("No testing/validation section detected");
        }

        return evidence;
    }

    private String buildJustification(int archWords, int techJustPairs,
                                      int processKwCount, boolean hasMethodSection,
                                      List<String> patterns,
                                      boolean hasTestingSection, int testWords, int testTables,
                                      double rawScore, PerformanceLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("The document was evaluated on its description of project methodology. ");

        // Architecture
        if (archWords == 0) {
            sb.append("No architecture section was found. ");
        } else {
            sb.append(String.format("Architecture sections total %d words. ", archWords));
        }

        // Tech justification
        if (techJustPairs == 0) {
            sb.append("Technology choices are not justified. ");
        } else {
            sb.append(String.format("%d technology-justification pair(s) were detected, " +
                    "indicating the student explains their tool choices. ", techJustPairs));
        }

        // Process
        if (processKwCount >= 7 || hasMethodSection) {
            sb.append("The development process is well-documented. ");
        } else if (processKwCount >= 3) {
            sb.append("Some development process description is present. ");
        } else {
            sb.append("Development process description is minimal. ");
        }

        // Patterns
        if (patterns.isEmpty()) {
            sb.append("No recognised design patterns were mentioned. ");
        } else {
            sb.append("Design patterns referenced: ").append(String.join(", ", patterns)).append(". ");
        }

        // Testing
        if (!hasTestingSection) {
            sb.append("No testing/validation section was found. ");
        } else if (testWords >= 300 && testTables > 0) {
            sb.append("Testing is thoroughly documented with comparison tables. ");
        } else if (testWords >= 100) {
            sb.append("Testing section is present but could be more detailed. ");
        } else {
            sb.append("Testing section exists but is very brief. ");
        }

        sb.append(String.format("Overall raw score: %.1f/100 (%s, %d points).",
                rawScore, level.name(), level.getPoints()));

        return sb.toString();
    }
}
