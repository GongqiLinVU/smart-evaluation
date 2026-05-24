package com.capstone.eval.dto;

import com.capstone.eval.model.TutorReview;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record TutorReviewResponse(
        Long id,
        Long submissionId,
        String tutorName,
        Integer overallScore,
        String overallComment,
        LocalDateTime reviewedAt,
        List<TutorReviewDimensionResponse> dimensions
) {
    public static TutorReviewResponse fromEntity(TutorReview review) {
        List<TutorReviewDimensionResponse> dims = review.getDimensions() != null
                ? review.getDimensions().stream()
                        .map(TutorReviewDimensionResponse::fromEntity)
                        .collect(Collectors.toList())
                : List.of();

        return new TutorReviewResponse(
                review.getId(),
                review.getSubmission().getId(),
                review.getTutor().getFullName(),
                review.getOverallScore(),
                review.getOverallComment(),
                review.getReviewedAt(),
                dims
        );
    }
}
