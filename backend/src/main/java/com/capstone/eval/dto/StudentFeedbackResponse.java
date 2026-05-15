package com.capstone.eval.dto;

import com.capstone.eval.model.StudentFeedback;

import java.time.LocalDateTime;

public record StudentFeedbackResponse(
        Long id,
        Long submissionId,
        String studentName,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
    public static StudentFeedbackResponse fromEntity(StudentFeedback fb) {
        return new StudentFeedbackResponse(
                fb.getId(),
                fb.getSubmission().getId(),
                fb.getUser().getFullName(),
                fb.getRating(),
                fb.getComment(),
                fb.getCreatedAt()
        );
    }
}
