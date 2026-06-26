package com.capstone.eval.repository;

import com.capstone.eval.model.TutorReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TutorReviewRepository extends JpaRepository<TutorReview, Long> {

    Optional<TutorReview> findFirstBySubmissionIdOrderByReviewedAtDesc(Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
