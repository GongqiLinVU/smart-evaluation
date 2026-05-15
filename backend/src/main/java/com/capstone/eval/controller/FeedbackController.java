package com.capstone.eval.controller;

import com.capstone.eval.dto.*;
import com.capstone.eval.model.*;
import com.capstone.eval.repository.*;
import com.capstone.eval.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackController {

    private final StudentFeedbackRepository feedbackRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final ScoreAdjustmentRepository adjustmentRepository;
    private final EvaluationResultRepository evaluationResultRepository;

    @PostMapping("/submissions/{submissionId}/feedback")
    public ResponseEntity<StudentFeedbackResponse> submitFeedback(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody StudentFeedbackRequest request) {

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentFeedback feedback = StudentFeedback.builder()
                .submission(submission)
                .user(user)
                .rating(request.rating())
                .comment(request.comment())
                .build();

        feedback = feedbackRepository.save(feedback);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StudentFeedbackResponse.fromEntity(feedback));
    }

    @GetMapping("/submissions/{submissionId}/feedback")
    public ResponseEntity<List<StudentFeedbackResponse>> getFeedback(@PathVariable Long submissionId) {
        List<StudentFeedbackResponse> feedbacks = feedbackRepository.findBySubmissionId(submissionId)
                .stream()
                .map(StudentFeedbackResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(feedbacks);
    }

    @PostMapping("/evaluations/{evaluationId}/adjust")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<ScoreAdjustmentResponse> adjustScore(
            @PathVariable Long evaluationId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ScoreAdjustmentRequest request) {

        EvaluationResult evaluation = evaluationResultRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));
        User tutor = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ScoreAdjustment adjustment = ScoreAdjustment.builder()
                .evaluation(evaluation)
                .tutor(tutor)
                .originalScore(evaluation.getOverallScore())
                .adjustedScore(request.adjustedScore())
                .reason(request.reason())
                .build();

        adjustment = adjustmentRepository.save(adjustment);

        evaluation.setOverallScore(request.adjustedScore());
        evaluationResultRepository.save(evaluation);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScoreAdjustmentResponse.fromEntity(adjustment));
    }

    @GetMapping("/evaluations/{evaluationId}/adjustments")
    public ResponseEntity<List<ScoreAdjustmentResponse>> getAdjustments(@PathVariable Long evaluationId) {
        List<ScoreAdjustmentResponse> adjustments = adjustmentRepository.findByEvaluationId(evaluationId)
                .stream()
                .map(ScoreAdjustmentResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(adjustments);
    }
}
