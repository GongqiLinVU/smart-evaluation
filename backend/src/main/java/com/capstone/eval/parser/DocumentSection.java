package com.capstone.eval.parser;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSection {

    private String heading;

    private int headingLevel;

    private String content;

    private int wordCount;

    @Builder.Default
    private List<String> codeSnippets = new ArrayList<>();

    private int imageCount;

    private int tableCount;

    @Builder.Default
    private List<String> tableContents = new ArrayList<>();

    @Builder.Default
    private List<String> imageDescriptions = new ArrayList<>();

    @Builder.Default
    private List<String> technicalTerms = new ArrayList<>();

    private double technicalVocabularyDensity;
}
