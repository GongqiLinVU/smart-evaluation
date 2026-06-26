package com.capstone.eval.repository;

import com.capstone.eval.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByProjectIdOrderByGroupCodeAsc(Long projectId);

    Optional<Group> findByProjectIdAndGroupCode(Long projectId, String groupCode);

    void deleteByProjectId(Long projectId);
}
