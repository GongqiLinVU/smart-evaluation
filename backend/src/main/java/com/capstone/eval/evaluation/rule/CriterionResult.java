package com.capstone.eval.evaluation.rule;

import com.capstone.eval.model.enums.PerformanceLevel;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Intermediate result produced by a single rule evaluation.
 * This is converted into a persisted {@link com.capstone.eval.model.CriterionScore}
 * by the {@link RuleBasedEngine}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CriterionResult {

    /** Name of the rubric criterion (e.g. "Logic & Explanation"). */
    private String criterionName;

    /** Normalised score on a 0-100 scale. */
    private double rawScore;

    /** Performance level derived from rawScore via {@link PerformanceLevel#fromRawScore}. */
    private PerformanceLevel level;

    /** Rubric points corresponding to the level (6/12/18/24/30). */
    private int points;

    /** Human-readable paragraph explaining how the score was derived. */
    private String justification;

    /** Specific evidence items found in the document. */
    private List<String> evidence;

    /** Breakdown by signal (e.g. "section_coverage" -> SubScore). */
    private Map<String, SubScore> subScores;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubScore {
        private double score;
        private double max;
        private String note;
    }
}
