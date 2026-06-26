package com.capstone.eval.dto;

import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.Submission;

import java.time.LocalDateTime;

public record SubmissionResponse(
        Long id,
        String studentName,
        String fileName,
        Long fileSizeBytes,
        String githubUrl,
        Integer version,
        Integer totalVersions,
        LocalDateTime uploadedAt,
        String status,
        Integer latestScore,
        String latestLevel,
        Integer latestMaxScore,
        Long projectId,
        String projectName,
        Long taskId,
        String taskName
) {

    public static SubmissionResponse fromEntity(Submission submission, EvaluationResult latestEval) {
        return fromEntity(submission, latestEval, null);
    }

    public static SubmissionResponse fromEntity(Submission submission, EvaluationResult latestEval, Integer totalVersions) {
        Integer score = null;
        String level = null;
        Integer maxScore = null;

        if (latestEval != null) {
            score = latestEval.getOverallScore();
            level = latestEval.getOverallLevel() != null
                    ? latestEval.getOverallLevel().name()
                    : null;
            maxScore = latestEval.getMaxScore() != null ? latestEval.getMaxScore() : 30;
        }

        return new SubmissionResponse(
                submission.getId(),
                submission.getStudentName(),
                submission.getFileName(),
                submission.getFileSizeBytes(),
                submission.getGithubUrl(),
                submission.getVersion(),
                totalVersions != null ? totalVersions : submission.getVersion(),
                submission.getUploadedAt(),
                submission.getStatus().name(),
                score,
                level,
                maxScore,
                submission.getProject() != null ? submission.getProject().getId() : null,
                submission.getProject() != null ? submission.getProject().getName() : null,
                submission.getTask() != null ? submission.getTask().getId() : null,
                submission.getTask() != null ? submission.getTask().getName() : null
        );
    }
}
