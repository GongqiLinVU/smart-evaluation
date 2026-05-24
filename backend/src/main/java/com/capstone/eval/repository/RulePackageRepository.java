package com.capstone.eval.repository;

import com.capstone.eval.model.RulePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RulePackageRepository extends JpaRepository<RulePackage, Long> {

    Optional<RulePackage> findByIsDefaultTrue();

    List<RulePackage> findAllByOrderByCreatedAtDesc();
}
