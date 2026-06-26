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
public class Observation {

    private String questionId;
    private Object answer;
    private Citation citation;
    private String note;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Citation {
        private int sectionIndex;
        private String sectionName;
        private String quote;
        private double confidence;
    }
}
