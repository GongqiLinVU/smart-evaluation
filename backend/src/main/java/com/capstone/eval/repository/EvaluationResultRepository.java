package com.capstone.eval.repository;

import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.enums.EvaluationMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationResultRepository extends JpaRepository<EvaluationResult, Long> {

    List<EvaluationResult> findBySubmissionId(Long submissionId);

    Optional<EvaluationResult> findBySubmissionIdAndMethod(Long submissionId, EvaluationMethod method);

    long countBySubmissionIdAndMethod(Long submissionId, EvaluationMethod method);

    List<EvaluationResult> findBySubmissionIdAndMethodOrderByEvaluatedAtDesc(Long submissionId, EvaluationMethod method);
}
