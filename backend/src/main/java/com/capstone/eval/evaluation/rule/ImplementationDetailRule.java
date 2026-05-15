package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule that assesses the level of concrete implementation detail in the
 * student's report -- code snippets, file references, schema documentation,
 * configuration detail, and section completeness.
 */
@Component
public class ImplementationDetailRule {

    private static final String CRITERION_NAME = "Implementation Detail";

    // Regex for file references: word.ext where ext is a known source extension
    private static final Pattern FILE_REF_PATTERN = Pattern.compile(
            "\\b[\\w./-]+\\.(py|java|js|ts|html|css|sql|jsx|tsx|yml|yaml|json|xml|kt|go|rb|php)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Regex for function/method references: camelCase(...) or snake_case(...)
    private static final Pattern FUNC_REF_PATTERN = Pattern.compile(
            "\\b([a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*|[a-z][a-z0-9]*(_[a-z0-9]+)+)\\s*\\("
    );

    // Keywords indicating schema / ERD discussion
    private static final List<String> SCHEMA_KEYWORDS = List.of(
            "schema", "erd", "entity relationship", "entity-relationship",
            "database design", "table structure", "data model"
    );

    // Keywords indicating configuration detail
    private static final List<String> CONFIG_KEYWORDS = List.of(
            "config", "setup", "install", "deploy", "environment", "port", "url",
            "endpoint", "base_url", "api_key", "timeout", "wal mode", "foreign key",
            ".env", "dockerfile", "docker-compose", "requirements.txt",
            "package.json", "application.properties", "application.yml"
    );

    private static final Set<String> SCHEMA_HEADING_KEYWORDS = Set.of(
            "schema", "database", "data model", "erd", "entity"
    );

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public CriterionResult evaluate(ParsedDocument doc) {
        String fullText = buildFullText(doc);

        // 1. Code snippet count (25%)
        int codeCount = doc.getCodeSnippetCount();
        double codeScore = scoreCodeSnippets(codeCount);

        // 2. File/function references (20%)
        int fileRefs = countFileReferences(fullText);
        int funcRefs = countFunctionReferences(fullText);
        int totalRefs = fileRefs + funcRefs;
        double refScore = scoreReferences(totalRefs);

        // 3. Schema/data documentation (20%)
        boolean hasTables = doc.getTableCount() > 0;
        boolean hasSchemaSection = hasSchemaSection(doc);
        boolean hasErdMention = containsKeyword(fullText, List.of("erd", "entity relationship",
                "entity-relationship"));
        double schemaScore = scoreSchema(hasTables, hasSchemaSection, hasErdMention);

        // 4. Configuration detail (15%)
        int configCount = countKeywords(fullText, CONFIG_KEYWORDS);
        double configScore = scoreConfig(configCount);

        // 5. Section completeness (20%)
        double completenessRatio = computeCompletenessRatio(doc);
        double completenessScore = completenessRatio * 100;

        // Weighted aggregate
        double rawScore = codeScore * 0.25
                + refScore * 0.20
                + schemaScore * 0.20
                + configScore * 0.15
                + completenessScore * 0.20;

        PerformanceLevel level = PerformanceLevel.fromRawScore(rawScore);

        // Sub-scores
        Map<String, CriterionResult.SubScore> subScores = new LinkedHashMap<>();
        subScores.put("code_snippets", CriterionResult.SubScore.builder()
                .score(codeScore).max(100)
                .note(codeCount + " code snippet(s) in document").build());
        subScores.put("file_function_references", CriterionResult.SubScore.builder()
                .score(refScore).max(100)
                .note(fileRefs + " file ref(s), " + funcRefs + " function ref(s)").build());
        subScores.put("schema_documentation", CriterionResult.SubScore.builder()
                .score(schemaScore).max(100)
                .note("tables=" + hasTables + ", schema_section=" + hasSchemaSection
                        + ", erd_mention=" + hasErdMention).build());
        subScores.put("configuration_detail", CriterionResult.SubScore.builder()
                .score(configScore).max(100)
                .note(configCount + " configuration keyword(s)").build());
        subScores.put("section_completeness", CriterionResult.SubScore.builder()
                .score(completenessScore).max(100)
                .note(String.format("%.0f%% of sections have >50 words",
                        completenessRatio * 100)).build());

        // Evidence
        List<String> evidence = buildEvidence(codeCount, fileRefs, funcRefs,
                hasTables, hasSchemaSection, hasErdMention, configCount, completenessRatio, doc);

        // Justification
        String justification = buildJustification(codeCount, totalRefs,
                hasTables, hasSchemaSection, hasErdMention, configCount,
                completenessRatio, rawScore, level);

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
    // Text helpers
    // -------------------------------------------------------------------

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

    private int countFileReferences(String text) {
        Matcher m = FILE_REF_PATTERN.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private int countFunctionReferences(String text) {
        Matcher m = FUNC_REF_PATTERN.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

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

    private boolean containsKeyword(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private boolean hasSchemaSection(ParsedDocument doc) {
        return doc.getSections().stream()
                .anyMatch(s -> {
                    if (s.getHeading() == null) return false;
                    String h = s.getHeading().toLowerCase();
                    return SCHEMA_HEADING_KEYWORDS.stream().anyMatch(h::contains);
                });
    }

    private double computeCompletenessRatio(ParsedDocument doc) {
        List<DocumentSection> sections = doc.getSections();
        if (sections.isEmpty()) return 0;
        long complete = sections.stream().filter(s -> s.getWordCount() > 50).count();
        return (double) complete / sections.size();
    }

    // -------------------------------------------------------------------
    // Scoring functions
    // -------------------------------------------------------------------

    private double scoreCodeSnippets(int count) {
        if (count == 0) return 10;
        if (count <= 2) return 40;
        if (count <= 5) return 60;
        if (count <= 10) return 80;
        return 100;
    }

    private double scoreReferences(int count) {
        if (count == 0) return 10;
        if (count <= 3) return 40;
        if (count <= 7) return 60;
        if (count <= 12) return 80;
        return 100;
    }

    private double scoreSchema(boolean hasTables, boolean hasSchemaSection, boolean hasErd) {
        if (!hasTables && !hasSchemaSection) return 10;
        if (hasTables && hasSchemaSection && hasErd) return 100;
        if (hasTables && hasSchemaSection) return 75;
        if (hasTables) return 50;
        // hasSchemaSection only
        return 40;
    }

    private double scoreConfig(int count) {
        if (count == 0) return 10;
        if (count <= 3) return 40;
        if (count <= 7) return 60;
        if (count <= 12) return 80;
        return 100;
    }

    // -------------------------------------------------------------------
    // Evidence & Justification
    // -------------------------------------------------------------------

    private List<String> buildEvidence(int codeCount, int fileRefs, int funcRefs,
                                       boolean hasTables, boolean hasSchemaSection,
                                       boolean hasErd, int configCount,
                                       double completenessRatio, ParsedDocument doc) {
        List<String> evidence = new ArrayList<>();

        evidence.add(codeCount + " code snippet(s) found in the document");
        evidence.add(fileRefs + " source file reference(s) and " + funcRefs
                + " function/method reference(s) detected");

        StringBuilder schemaParts = new StringBuilder("Schema documentation: ");
        schemaParts.append("tables=").append(hasTables ? "yes" : "no");
        schemaParts.append(", schema section=").append(hasSchemaSection ? "yes" : "no");
        schemaParts.append(", ERD mention=").append(hasErd ? "yes" : "no");
        evidence.add(schemaParts.toString());

        evidence.add(configCount + " configuration-related keyword(s) detected");

        long totalSections = doc.getSections().size();
        long completeSections = doc.getSections().stream()
                .filter(s -> s.getWordCount() > 50).count();
        evidence.add(completeSections + " of " + totalSections
                + " sections are non-skeletal (>50 words)");

        return evidence;
    }

    private String buildJustification(int codeCount, int totalRefs,
                                      boolean hasTables, boolean hasSchemaSection,
                                      boolean hasErd, int configCount,
                                      double completenessRatio,
                                      double rawScore, PerformanceLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("The document was evaluated on the depth of its implementation details. ");

        // Code
        if (codeCount == 0) {
            sb.append("No code snippets were included, which makes it difficult to assess " +
                    "the actual implementation. ");
        } else if (codeCount <= 2) {
            sb.append(String.format("%d code snippet(s) were included, providing some " +
                    "implementation evidence. ", codeCount));
        } else {
            sb.append(String.format("%d code snippets demonstrate concrete implementation work. ",
                    codeCount));
        }

        // References
        if (totalRefs == 0) {
            sb.append("No file or function references were detected. ");
        } else {
            sb.append(String.format("%d file/function reference(s) show traceability to actual code. ",
                    totalRefs));
        }

        // Schema
        if (hasTables && hasSchemaSection && hasErd) {
            sb.append("Data modelling is well-documented with tables, a schema section, and ERD references. ");
        } else if (hasTables) {
            sb.append("Some data documentation is present via tables. ");
        } else {
            sb.append("Data/schema documentation is minimal. ");
        }

        // Config
        if (configCount >= 8) {
            sb.append("Configuration and deployment details are thoroughly described. ");
        } else if (configCount >= 4) {
            sb.append("Some configuration details are provided. ");
        } else {
            sb.append("Configuration details are sparse. ");
        }

        // Completeness
        sb.append(String.format("%.0f%% of sections are substantive (>50 words). ",
                completenessRatio * 100));

        sb.append(String.format("Overall raw score: %.1f/100 (%s, %d points).",
                rawScore, level.name(), level.getPoints()));

        return sb.toString();
    }
}
