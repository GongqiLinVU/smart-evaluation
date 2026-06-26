package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CompletionDemoRule {

    private static final String CRITERION_NAME = "80% Completion Demonstration";

    private static final Set<String> FEATURE_INDICATORS = Set.of(
            "feature", "component", "module", "function", "system",
            "service", "page", "screen", "endpoint");
    private static final Set<String> WORKING_INDICATORS = Set.of(
            "working", "functional", "operational", "completed", "implemented",
            "able to", "successfully", "running", "deployed", "tested",
            "set up", "connected", "integrated", "configured");
    private static final Set<String> STATUS_INDICATORS = Set.of(
            "completed", "in progress", "done", "finished", "remaining",
            "not started", "ready", "100%", "80%", "functional",
            "working on", "currently");
    private static final Set<String> INTEGRATION_INDICATORS = Set.of(
            "connect", "integrate", "link", "together", "communicate",
            "between", "calls", "sends to", "receives from", "depends on",
            "works with", "interacts", "combined", "whole app", "end to end",
            "flow", "pipeline", "data from");
    private static final Set<String> TARGET_INDICATORS = Set.of(
            "target", "goal", "requirement", "should", "must", "expected",
            "plan", "aim", "objective", "deliver", "main function");

    public CriterionResult evaluate(ParsedDocument doc) {
        Map<String, CriterionResult.SubScore> subScores = new LinkedHashMap<>();
        List<String> evidence = new ArrayList<>();

        List<DocumentSection> featureSections = findFeatureSections(doc);
        int featureCount = featureSections.size();
        String fullDocText = getFullDocText(doc).toLowerCase();

        // 1. Main features/functions listed (20%)
        double featureScore;
        if (featureCount >= 6) {
            featureScore = 100;
            evidence.add(featureCount + " distinct features/components documented");
        } else if (featureCount >= 4) {
            featureScore = 75;
            evidence.add(featureCount + " features documented");
        } else if (featureCount >= 2) {
            featureScore = 50;
            evidence.add(featureCount + " features documented");
        } else if (featureCount >= 1) {
            featureScore = 25;
            evidence.add(featureCount + " feature documented");
        } else {
            featureScore = 0;
            evidence.add("No feature sections found");
        }
        subScores.put("feature_count", CriterionResult.SubScore.builder()
                .score(featureScore).max(100).note(featureCount + " features documented").build());

        // 2. Feature status / working evidence (25%)
        //    Each feature should state whether it is working/completed/in-progress.
        int statusCount = 0;
        for (DocumentSection section : featureSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, WORKING_INDICATORS) || containsAny(combined, STATUS_INDICATORS)) {
                statusCount++;
            }
        }
        double statusScore = featureCount > 0 ? (statusCount * 100.0 / featureCount) : 0;
        subScores.put("feature_status", CriterionResult.SubScore.builder()
                .score(statusScore).max(100)
                .note(statusCount + "/" + featureCount + " features state their working status").build());
        if (statusCount > 0) evidence.add(statusCount + " features describe working/completion status");

        // 3. Target definition — what the feature SHOULD do (20%)
        //    Progress report must define targets so completion can be verified.
        int targetCount = 0;
        for (DocumentSection section : featureSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, TARGET_INDICATORS)) {
                targetCount++;
            }
        }
        double targetScore = featureCount > 0 ? (targetCount * 100.0 / featureCount) : 0;
        subScores.put("target_definition", CriterionResult.SubScore.builder()
                .score(targetScore).max(100)
                .note(targetCount + "/" + featureCount + " features define their target/goal").build());
        if (targetCount == 0) evidence.add("No features define their target — cannot verify completion against plan");

        // 4. Feature integration — how features connect as a whole app (20%)
        //    Tutor requirement: show how features link together, not just independently.
        int integrationCount = 0;
        for (DocumentSection section : featureSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, INTEGRATION_INDICATORS)) {
                integrationCount++;
            }
        }
        boolean hasOverviewIntegration = fullDocText.contains("overall") || fullDocText.contains("architecture")
                || fullDocText.contains("system overview") || fullDocText.contains("how they work together");
        double integrationScore = 0;
        if (featureCount > 0) {
            integrationScore = integrationCount * 100.0 / featureCount;
        }
        if (hasOverviewIntegration) integrationScore = Math.min(100, integrationScore + 30);
        subScores.put("feature_integration", CriterionResult.SubScore.builder()
                .score(integrationScore).max(100)
                .note(integrationCount + "/" + featureCount + " features describe integration with others").build());
        if (integrationCount == 0 && !hasOverviewIntegration) {
            evidence.add("Features listed independently — no description of how they connect as whole app");
        } else {
            evidence.add(integrationCount + " features describe integration with other components");
        }

        // 5. Visual evidence with captions (15%)
        //    Screenshots are good; screenshots WITH captions/descriptions are rewarded more.
        //    Captions = text near image like "Figure X: ...", alt-text, or descriptive paragraph.
        int sectionsWithImages = 0;
        int totalImages = 0;
        int captionedImages = 0;
        for (DocumentSection section : featureSections) {
            if (section.getImageCount() > 0) {
                sectionsWithImages++;
                totalImages += section.getImageCount();
                if (hasCaptions(section)) {
                    captionedImages++;
                }
            }
        }
        double imageScore;
        if (totalImages == 0) {
            imageScore = 0;
        } else {
            double baseImageScore = featureCount > 0 ?
                    Math.min(100, sectionsWithImages * 100.0 / featureCount) : 0;
            double captionBonus = sectionsWithImages > 0 ?
                    (captionedImages * 30.0 / sectionsWithImages) : 0;
            imageScore = Math.min(100, baseImageScore + captionBonus);
        }
        subScores.put("visual_evidence", CriterionResult.SubScore.builder()
                .score(imageScore).max(100)
                .note(totalImages + " images across " + sectionsWithImages + " sections, "
                        + captionedImages + " with captions").build());
        if (totalImages > 0 && captionedImages > 0) {
            evidence.add(totalImages + " screenshots found, " + captionedImages + " with captions/descriptions (good)");
        } else if (totalImages > 0) {
            evidence.add(totalImages + " screenshots found but lacking captions — add descriptions to explain what each shows");
        } else {
            evidence.add("No screenshots or visual evidence of working features");
        }

        // Weights: features(20%) + status(25%) + targets(20%) + integration(20%) + images(15%)
        double rawScore = featureScore * 0.20 + statusScore * 0.25 + targetScore * 0.20
                + integrationScore * 0.20 + imageScore * 0.15;

        PerformanceLevel level = PerformanceLevel.fromRawScore(rawScore);
        String justification = String.format(
                "%d features documented. %d/%d state working status, " +
                        "%d/%d define targets, %d/%d describe integration, " +
                        "%d contain visual evidence (%d captioned). Raw score: %.0f/100 (%s).",
                featureCount, statusCount, featureCount, targetCount, featureCount,
                integrationCount, featureCount, sectionsWithImages, captionedImages,
                rawScore, level.name());

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

    private List<DocumentSection> findFeatureSections(ParsedDocument doc) {
        List<DocumentSection> results = new ArrayList<>();
        for (DocumentSection section : doc.getSections()) {
            String heading = section.getHeading().toLowerCase();
            if (heading.contains("meeting") || heading.contains("minutes")) continue;
            if (heading.contains("introduction")) continue;
            if (heading.contains("meeting records")) continue;

            String combined = getCombinedContent(section);
            if (combined.length() > 50) {
                if (containsAny(combined.toLowerCase(), FEATURE_INDICATORS)
                        || section.getHeadingLevel() == 2) {
                    results.add(section);
                }
            }
        }
        return results;
    }

    private String getFullDocText(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (DocumentSection section : doc.getSections()) {
            sb.append(getCombinedContent(section)).append(" ");
        }
        return sb.toString();
    }

    private String getCombinedContent(DocumentSection section) {
        StringBuilder sb = new StringBuilder();
        if (section.getContent() != null) sb.append(section.getContent());
        if (section.getTableContents() != null) {
            for (String table : section.getTableContents()) {
                sb.append(" ").append(table);
            }
        }
        return sb.toString();
    }

    private boolean hasCaptions(DocumentSection section) {
        String content = section.getContent() != null ? section.getContent().toLowerCase() : "";
        if (content.contains("figure") || content.contains("fig.") || content.contains("screenshot")
                || content.contains("screen shot") || content.contains("as shown")
                || content.contains("the above") || content.contains("the below")
                || content.contains("image shows") || content.contains("this shows")) {
            return true;
        }
        if (section.getImageDescriptions() != null) {
            for (String desc : section.getImageDescriptions()) {
                if (desc != null && desc.length() > 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
