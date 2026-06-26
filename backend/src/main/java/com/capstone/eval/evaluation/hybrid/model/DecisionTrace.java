package com.capstone.eval.evaluation.hybrid.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecisionTrace {

    private String criterionKey;
    private double totalPoints;
    private double maxPoints;
    private String performanceLevel;
    private List<RuleResult> rulesTriggered;
    private String explanation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleResult {
        private String ruleId;
        private Object fired;
        private double points;
        private String reason;
    }
}
