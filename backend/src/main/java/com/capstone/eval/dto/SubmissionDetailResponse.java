package com.capstone.eval.dto;

import java.util.List;

public record SubmissionDetailResponse(
        SubmissionResponse submission,
        DocumentStatsResponse documentStats,
        List<EvaluationResultResponse> evaluations
) {
}
