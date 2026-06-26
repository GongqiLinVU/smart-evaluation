package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class ProgressOrganizationRule {

    private static final String CRITERION_NAME = "Progress Diary Organization";

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}");
    private static final Set<String> TEAM_KEYWORDS = Set.of(
            "team member", "member", "assigned", "contributor", "responsible");
    private static final Set<String> STATUS_KEYWORDS = Set.of(
            "completed", "in progress", "not started", "done", "finished",
            "remaining", "status", "current progress", "milestone",
            "100%", "80%", "50%", "blocked", "ready");
    private static final Set<String> TARGET_KEYWORDS = Set.of(
            "target", "goal", "objective", "aim", "milestone", "requirement",
            "deliverable", "expected", "plan to", "should be able to");
    private static final Set<String> PROBLEM_KEYWORDS = Set.of(
            "issue", "problem", "blocker", "stuck", "delayed", "challenge",
            "difficulty", "bug", "error", "need to fix", "not working");

    public CriterionResult evaluate(ParsedDocument doc) {
        List<DocumentSection> progressSections = findProgressSections(doc);
        Map<String, CriterionResult.SubScore> subScores = new LinkedHashMap<>();
        List<String> evidence = new ArrayList<>();

        // 1. Feature status clarity — does each section state its current status? (30%)
        //    Diary tables are fine as reference, but status declaration is what matters.
        int statusCount = 0;
        for (DocumentSection section : progressSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, STATUS_KEYWORDS)) {
                statusCount++;
            }
        }
        double statusScore = progressSections.isEmpty() ? 0 :
                (statusCount * 100.0 / progressSections.size());
        subScores.put("feature_status", CriterionResult.SubScore.builder()
                .score(statusScore).max(100)
                .note(statusCount + "/" + progressSections.size() + " sections declare feature status").build());
        if (statusCount > 0) {
            evidence.add(statusCount + " sections clearly state feature status");
        } else {
            evidence.add("No clear feature status declarations found — only diary tables without status summary");
        }

        // 2. Target/goal definition per feature (25%)
        //    Progress report must define what the target IS so completion can be measured.
        int targetCount = 0;
        for (DocumentSection section : progressSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, TARGET_KEYWORDS)) {
                targetCount++;
            }
        }
        double targetScore = progressSections.isEmpty() ? 0 :
                (targetCount * 100.0 / progressSections.size());
        subScores.put("target_definition", CriterionResult.SubScore.builder()
                .score(targetScore).max(100)
                .note(targetCount + "/" + progressSections.size() + " sections define targets/goals").build());
        if (targetCount > 0) evidence.add(targetCount + " sections define feature targets");
        if (targetCount == 0) evidence.add("No target/goal definitions found — cannot verify progress against plan");

        // 3. Problems/blockers identified (15%)
        int problemCount = 0;
        for (DocumentSection section : progressSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, PROBLEM_KEYWORDS)) {
                problemCount++;
            }
        }
        double problemScore = progressSections.isEmpty() ? 0 :
                Math.min(100, problemCount * 100.0 / Math.max(1, progressSections.size() / 2.0));
        subScores.put("problems_identified", CriterionResult.SubScore.builder()
                .score(problemScore).max(100)
                .note(problemCount + " sections identify problems/blockers").build());

        // 4. Diary tables as supporting reference (15%)
        //    Tables are OK and expected, but they're supporting material, not the core.
        int entryCount = 0;
        for (DocumentSection section : progressSections) {
            entryCount += section.getTableCount();
        }
        double diaryScore;
        if (entryCount >= 5) {
            diaryScore = 100;
        } else if (entryCount >= 3) {
            diaryScore = 75;
        } else if (entryCount >= 1) {
            diaryScore = 50;
        } else {
            diaryScore = 0;
        }
        evidence.add(entryCount + " progress diary tables found (supporting reference)");
        subScores.put("diary_tables", CriterionResult.SubScore.builder()
                .score(diaryScore).max(100).note(entryCount + " diary tables as supporting reference").build());

        // 5. Team contributions and logical flow (15%)
        int teamMentions = 0;
        int datedTables = 0;
        for (DocumentSection section : progressSections) {
            String combined = getCombinedContent(section).toLowerCase();
            if (containsAny(combined, TEAM_KEYWORDS)) teamMentions++;
            if (section.getTableContents() != null) {
                for (String table : section.getTableContents()) {
                    if (table.toLowerCase().contains("date") || DATE_PATTERN.matcher(table).find()) {
                        datedTables++;
                    }
                }
            }
        }
        double teamFlowScore = 0;
        if (teamMentions > 0) teamFlowScore += 50;
        if (progressSections.size() >= 3 && datedTables >= 3) teamFlowScore += 50;
        subScores.put("team_and_flow", CriterionResult.SubScore.builder()
                .score(teamFlowScore).max(100)
                .note(teamMentions + " sections mention team; " + datedTables + " dated tables").build());
        if (teamMentions > 0) evidence.add("Team contributions documented in " + teamMentions + " sections");

        // Weighted: status (30%) + targets (25%) + problems (15%) + diary (15%) + team/flow (15%)
        double rawScore = statusScore * 0.30 + targetScore * 0.25 + problemScore * 0.15
                + diaryScore * 0.15 + teamFlowScore * 0.15;

        PerformanceLevel level = PerformanceLevel.fromRawScore(rawScore);
        String justification = String.format(
                "Progress report has %d feature sections. Status clarity: %.0f%% (%d/%d declare status), " +
                        "Target definitions: %.0f%% (%d/%d define targets), " +
                        "Problems identified: %d sections, Diary tables: %d (supporting). " +
                        "Raw score: %.0f/100 (%s).",
                progressSections.size(), statusScore, statusCount, progressSections.size(),
                targetScore, targetCount, progressSections.size(),
                problemCount, entryCount, rawScore, level.name());

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

    private List<DocumentSection> findProgressSections(ParsedDocument doc) {
        List<DocumentSection> results = new ArrayList<>();
        for (DocumentSection section : doc.getSections()) {
            String heading = section.getHeading().toLowerCase();
            if (heading.contains("meeting") || heading.contains("minutes")) continue;
            if (heading.contains("introduction") || heading.contains("database schema")) continue;

            String combined = getCombinedContent(section);
            if (section.getTableCount() > 0 || combined.length() > 100) {
                results.add(section);
            }
        }
        return results;
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

    private boolean containsAny(String text, Set<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
