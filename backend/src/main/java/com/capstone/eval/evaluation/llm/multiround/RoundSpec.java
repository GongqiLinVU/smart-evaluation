package com.capstone.eval.evaluation.llm.multiround;

import java.util.List;

public record RoundSpec(
        int roundNumber,
        String roundType,
        List<Integer> sectionIndices,
        List<String> criteriaKeys,
        int estimatedWordCount
) {
    public static final String TYPE_SECTION_EVAL = "SECTION_EVAL";
    public static final String TYPE_SYNTHESIS = "SYNTHESIS";
}
