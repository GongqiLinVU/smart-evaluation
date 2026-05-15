package com.capstone.eval.repository;

import com.capstone.eval.model.ScoreAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreAdjustmentRepository extends JpaRepository<ScoreAdjustment, Long> {

    List<ScoreAdjustment> findByEvaluationId(Long evaluationId);
}
