package com.capstone.eval.evaluation.hybrid.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvidenceQuestion {

    private String id;
    private String text;
    private QuestionType type;
    @Builder.Default
    private boolean required = true;

    public enum QuestionType {
        BOOLEAN,
        TERNARY,
        COUNT,
        LIKERT_5,
        TEXT
    }
}
