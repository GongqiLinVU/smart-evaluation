package com.capstone.eval.dto;

import java.time.LocalDateTime;

public record GroupSubmissionInfoResponse(
        Long groupId,
        String groupCode,
        Long submissionId,
        String fileName,
        Long fileSizeBytes,
        String status,
        LocalDateTime uploadedAt,
        DocumentStatsResponse documentStats,
        boolean imageHeavy
) {}
