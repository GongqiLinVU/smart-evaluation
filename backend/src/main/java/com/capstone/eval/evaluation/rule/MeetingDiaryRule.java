package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class MeetingDiaryRule {

    private static final String CRITERION_NAME = "Meeting Diary Consistency";

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}");
    private static final Set<String> MEETING_KEYWORDS = Set.of(
            "meeting", "minutes", "record", "agenda", "discussion");
    private static final Set<String> ATTENDEE_KEYWORDS = Set.of(
            "present", "absent", "attend", "member", "team member");
    private static final Set<String> ACTION_KEYWORDS = Set.of(
            "action", "task", "objective", "todo", "assigned",
            "follow up", "responsible", "next steps");
    private static final Set<String> AGENDA_KEYWORDS = Set.of(
            "agenda", "topic", "issue", "discussion", "point");

    public CriterionResult evaluate(ParsedDocument doc) {
        List<DocumentSection> meetingSections = findMeetingSections(doc);
        int meetingCount = meetingSections.size();

        Map<String, CriterionResult.SubScore> subScores = new LinkedHashMap<>();
        List<String> evidence = new ArrayList<>();

        // 1. Number of meeting entries (25%)
        double entryScore;
        if (meetingCount >= 8) {
            entryScore = 100;
            evidence.add("Found " + meetingCount + " meeting entries (comprehensive record)");
        } else if (meetingCount >= 5) {
            entryScore = 75;
            evidence.add("Found " + meetingCount + " meeting entries (good coverage)");
        } else if (meetingCount >= 3) {
            entryScore = 50;
            evidence.add("Found " + meetingCount + " meeting entries (moderate)");
        } else if (meetingCount >= 1) {
            entryScore = 25;
            evidence.add("Found " + meetingCount + " meeting entries (limited)");
        } else {
            entryScore = 0;
            evidence.add("No meeting entries found");
        }
        subScores.put("entry_count", CriterionResult.SubScore.builder()
                .score(entryScore).max(100).note(meetingCount + " meetings documented").build());

        // 2. Dates present (20%)
        int datedCount = 0;
        for (DocumentSection section : meetingSections) {
            String allContent = getCombinedContent(section);
            if (DATE_PATTERN.matcher(allContent).find()) {
                datedCount++;
            }
        }
        double dateScore = meetingCount > 0 ? (datedCount * 100.0 / meetingCount) : 0;
        subScores.put("dates_present", CriterionResult.SubScore.builder()
                .score(dateScore).max(100).note(datedCount + "/" + meetingCount + " entries dated").build());
        if (datedCount > 0) evidence.add(datedCount + " of " + meetingCount + " meetings have dates");

        // 3. Attendees listed (15%)
        int attendeeCount = 0;
        for (DocumentSection section : meetingSections) {
            String allContent = getCombinedContent(section).toLowerCase();
            if (containsAny(allContent, ATTENDEE_KEYWORDS)) {
                attendeeCount++;
            }
        }
        double attendeeScore = meetingCount > 0 ? (attendeeCount * 100.0 / meetingCount) : 0;
        subScores.put("attendees_listed", CriterionResult.SubScore.builder()
                .score(attendeeScore).max(100).note(attendeeCount + "/" + meetingCount + " list attendees").build());

        // 4. Agenda/discussion points (20%)
        int agendaCount = 0;
        for (DocumentSection section : meetingSections) {
            String allContent = getCombinedContent(section).toLowerCase();
            if (containsAny(allContent, AGENDA_KEYWORDS)) {
                agendaCount++;
            }
        }
        double agendaScore = meetingCount > 0 ? (agendaCount * 100.0 / meetingCount) : 0;
        subScores.put("agenda_items", CriterionResult.SubScore.builder()
                .score(agendaScore).max(100).note(agendaCount + "/" + meetingCount + " include agenda/topics").build());

        // 5. Action items/next steps (20%)
        int actionCount = 0;
        for (DocumentSection section : meetingSections) {
            String allContent = getCombinedContent(section).toLowerCase();
            if (containsAny(allContent, ACTION_KEYWORDS)) {
                actionCount++;
            }
        }
        double actionScore = meetingCount > 0 ? (actionCount * 100.0 / meetingCount) : 0;
        subScores.put("action_items", CriterionResult.SubScore.builder()
                .score(actionScore).max(100).note(actionCount + "/" + meetingCount + " have action items/next steps").build());

        // Weighted aggregate
        double rawScore = entryScore * 0.25 + dateScore * 0.20 + attendeeScore * 0.15
                + agendaScore * 0.20 + actionScore * 0.20;

        PerformanceLevel level = PerformanceLevel.fromRawScore(rawScore);
        String justification = buildJustification(meetingCount, datedCount, attendeeCount,
                agendaCount, actionCount, rawScore, level);

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

    private List<DocumentSection> findMeetingSections(ParsedDocument doc) {
        List<DocumentSection> results = new ArrayList<>();
        for (DocumentSection section : doc.getSections()) {
            String heading = section.getHeading().toLowerCase();
            if (heading.contains("meeting") || heading.contains("minutes")) {
                String combined = getCombinedContent(section);
                if (combined.length() > 20) {
                    results.add(section);
                }
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

    private String buildJustification(int meetingCount, int datedCount, int attendeeCount,
                                      int agendaCount, int actionCount, double rawScore,
                                      PerformanceLevel level) {
        return String.format("Found %d meeting entries. %d/%d dated, %d/%d list attendees, " +
                        "%d/%d include agenda items, %d/%d have action items/next steps. " +
                        "Raw score: %.0f/100 (%s).",
                meetingCount, datedCount, meetingCount, attendeeCount, meetingCount,
                agendaCount, meetingCount, actionCount, meetingCount, rawScore, level.name());
    }
}
