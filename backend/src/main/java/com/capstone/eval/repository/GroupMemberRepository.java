package com.capstone.eval.repository;

import com.capstone.eval.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupId(Long groupId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.email = :email AND gm.group.project.id = :projectId")
    Optional<GroupMember> findByEmailAndProjectId(String email, Long projectId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.user.id = :userId AND gm.group.project.id = :projectId")
    Optional<GroupMember> findByUserIdAndProjectId(Long userId, Long projectId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.studentId = :studentId AND gm.group.project.id = :projectId")
    Optional<GroupMember> findByStudentIdAndProjectId(String studentId, Long projectId);

    void deleteByGroupId(Long groupId);
}
