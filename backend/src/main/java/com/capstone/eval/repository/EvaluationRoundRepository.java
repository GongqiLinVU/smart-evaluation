package com.capstone.eval.repository;

import com.capstone.eval.model.EvaluationRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRoundRepository extends JpaRepository<EvaluationRound, Long> {

    List<EvaluationRound> findByEvaluationResultIdOrderByRoundNumberAsc(Long evaluationResultId);
}
