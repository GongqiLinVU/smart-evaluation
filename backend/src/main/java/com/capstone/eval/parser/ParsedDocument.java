package com.capstone.eval.parser;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    @Builder.Default
    private List<DocumentSection> sections = new ArrayList<>();

    private int totalWordCount;

    private int headingCount;

    private int codeSnippetCount;

    private int tableCount;

    private int imageCount;

    private int linkCount;

    @Builder.Default
    private List<String> externalLinks = new ArrayList<>();

    private boolean hasExecutiveSummary;

    private boolean hasArchitectureSection;

    private boolean hasTestingSection;

    private boolean hasConclusionSection;

    private boolean hasMethodologySection;

    private boolean hasImplementationSection;
}
