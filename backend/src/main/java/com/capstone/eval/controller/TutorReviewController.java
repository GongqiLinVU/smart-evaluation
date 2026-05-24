package com.capstone.eval.controller;

import com.capstone.eval.dto.TutorReviewDimensionRequest;
import com.capstone.eval.dto.TutorReviewRequest;
import com.capstone.eval.dto.TutorReviewResponse;
import com.capstone.eval.model.*;
import com.capstone.eval.repository.SubmissionRepository;
import com.capstone.eval.repository.TutorReviewRepository;
import com.capstone.eval.repository.UserRepository;
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
@RequestMapping("/api/submissions/{submissionId}/tutor-reviews")
@RequiredArgsConstructor
public class TutorReviewController {

    private final TutorReviewRepository tutorReviewRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<TutorReviewResponse> createOrUpdateReview(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TutorReviewRequest request) {

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        User tutor = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Find existing review for this submission (only one allowed)
        TutorReview review = tutorReviewRepository
                .findFirstBySubmissionIdOrderByReviewedAtDesc(submissionId)
                .orElse(null);

        if (review == null) {
            review = TutorReview.builder()
                    .submission(submission)
                    .tutor(tutor)
                    .overallScore(request.overallScore())
                    .overallComment(request.overallComment())
                    .build();
        } else {
            review.setTutor(tutor);
            review.setOverallScore(request.overallScore());
            review.setOverallComment(request.overallComment());
            review.getDimensions().clear();
        }

        final TutorReview finalReview = review;
        List<TutorReviewDimension> dimensions = request.dimensions().stream()
                .map(d -> TutorReviewDimension.builder()
                        .tutorReview(finalReview)
                        .dimensionName(d.dimensionName())
                        .score(d.score())
                        .maxScore(d.maxScore())
                        .justification(d.justification())
                        .build())
                .collect(Collectors.toList());

        review.getDimensions().addAll(dimensions);
        TutorReview saved = tutorReviewRepository.save(review);

        return ResponseEntity.ok(TutorReviewResponse.fromEntity(saved));
    }

    @GetMapping
    public ResponseEntity<TutorReviewResponse> getReview(@PathVariable Long submissionId) {
        return tutorReviewRepository
                .findFirstBySubmissionIdOrderByReviewedAtDesc(submissionId)
                .map(r -> ResponseEntity.ok(TutorReviewResponse.fromEntity(r)))
                .orElse(ResponseEntity.ok(null));
    }

    @PostMapping("/dimensions")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<TutorReviewResponse> addDimension(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TutorReviewDimensionRequest request) {

        TutorReview review = tutorReviewRepository
                .findFirstBySubmissionIdOrderByReviewedAtDesc(submissionId)
                .orElseThrow(() -> new RuntimeException(
                        "No tutor review exists yet. Create one first."));

        TutorReviewDimension dimension = TutorReviewDimension.builder()
                .tutorReview(review)
                .dimensionName(request.dimensionName())
                .score(request.score())
                .maxScore(request.maxScore())
                .justification(request.justification())
                .build();

        review.getDimensions().add(dimension);
        TutorReview saved = tutorReviewRepository.save(review);

        return ResponseEntity.ok(TutorReviewResponse.fromEntity(saved));
    }

    @DeleteMapping("/dimensions/{dimensionId}")
    @PreAuthorize("hasAnyRole('TUTOR', 'ADMIN')")
    public ResponseEntity<TutorReviewResponse> removeDimension(
            @PathVariable Long submissionId,
            @PathVariable Long dimensionId) {

        TutorReview review = tutorReviewRepository
                .findFirstBySubmissionIdOrderByReviewedAtDesc(submissionId)
                .orElseThrow(() -> new RuntimeException("No tutor review found"));

        review.getDimensions().removeIf(d -> d.getId().equals(dimensionId));
        TutorReview saved = tutorReviewRepository.save(review);

        return ResponseEntity.ok(TutorReviewResponse.fromEntity(saved));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteReview(@PathVariable Long submissionId) {
        tutorReviewRepository
                .findFirstBySubmissionIdOrderByReviewedAtDesc(submissionId)
                .ifPresent(tutorReviewRepository::delete);
        return ResponseEntity.noContent().build();
    }
}
