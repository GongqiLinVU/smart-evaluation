package com.capstone.eval.repository;

import com.capstone.eval.model.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {

    List<ProjectTask> findByProjectIdOrderByDisplayOrderAsc(Long projectId);

    int countByProjectId(Long projectId);
}
