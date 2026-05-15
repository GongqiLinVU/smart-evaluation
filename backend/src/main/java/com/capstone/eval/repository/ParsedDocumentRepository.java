package com.capstone.eval.repository;

import com.capstone.eval.model.ParsedDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParsedDocumentRepository extends JpaRepository<ParsedDocumentEntity, Long> {

    Optional<ParsedDocumentEntity> findBySubmissionId(Long submissionId);
}
