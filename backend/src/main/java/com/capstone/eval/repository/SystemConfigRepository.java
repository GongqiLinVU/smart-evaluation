package com.capstone.eval.repository;

import com.capstone.eval.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    List<SystemConfig> findByKeyStartingWith(String prefix);
}
