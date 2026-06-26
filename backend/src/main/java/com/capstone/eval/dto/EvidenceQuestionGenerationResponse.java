package com.capstone.eval.dto;

import com.capstone.eval.evaluation.hybrid.model.EvidenceQuestionSet;
import com.capstone.eval.evaluation.hybrid.model.ScoringRuleSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceQuestionGenerationResponse {

    private EvidenceQuestionSet questions;
    private ScoringRuleSet scoringRules;
    private boolean saved;
}
