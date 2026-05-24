package com.capstone.eval.repository;

import com.capstone.eval.model.LlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmConfigRepository extends JpaRepository<LlmConfig, Long> {

    Optional<LlmConfig> findByIsDefaultTrue();

    Optional<LlmConfig> findByName(String name);

    List<LlmConfig> findAllByOrderByCreatedAtDesc();
}
