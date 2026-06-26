package com.capstone.eval.dto;

import java.util.List;

public record BulkUploadResponse(
        int uploaded,
        int groupsCreated,
        int groupsMatched,
        List<UploadedItem> submissions,
        List<UploadError> errors
) {
    public record UploadedItem(Long submissionId, String groupCode, String filename) {}
    public record UploadError(String filename, String reason) {}
}
