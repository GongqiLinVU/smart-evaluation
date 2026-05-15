package com.capstone.eval.repository;

import com.capstone.eval.model.StudentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentFeedbackRepository extends JpaRepository<StudentFeedback, Long> {

    List<StudentFeedback> findBySubmissionId(Long submissionId);

    List<StudentFeedback> findByUserId(Long userId);
}
