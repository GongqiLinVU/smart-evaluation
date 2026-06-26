package com.capstone.eval.repository;

import com.capstone.eval.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findAllByOrderByUploadedAtDesc();

    List<Submission> findByUserIdOrderByUploadedAtDesc(Long userId);

    List<Submission> findByUserIdOrderByVersionDesc(Long userId);

    @Query("SELECT COALESCE(MAX(s.version), 0) FROM Submission s WHERE s.user.id = :userId")
    int findMaxVersionByUserId(Long userId);

    Optional<Submission> findByIdAndUserId(Long id, Long userId);

    List<Submission> findByStudentNameOrderByVersionDesc(String studentName);

    @Query("SELECT s FROM Submission s WHERE s.version = " +
            "(SELECT MAX(s2.version) FROM Submission s2 WHERE s2.studentName = s.studentName) " +
            "ORDER BY s.uploadedAt DESC")
    List<Submission> findLatestPerStudent();

    @Query("SELECT s FROM Submission s WHERE s.user.id = :userId AND s.version = " +
            "(SELECT MAX(s2.version) FROM Submission s2 WHERE s2.user.id = :userId)")
    Optional<Submission> findLatestByUserId(Long userId);

    // Project-scoped queries
    List<Submission> findByProjectIdOrderByUploadedAtDesc(Long projectId);

    @Query("SELECT s FROM Submission s WHERE s.project.id = :projectId AND s.version = " +
            "(SELECT MAX(s2.version) FROM Submission s2 WHERE s2.studentName = s.studentName AND s2.project.id = :projectId) " +
            "ORDER BY s.uploadedAt DESC")
    List<Submission> findLatestPerStudentByProjectId(Long projectId);

    @Query("SELECT COALESCE(MAX(s.version), 0) FROM Submission s WHERE s.user.id = :userId AND s.project.id = :projectId")
    int findMaxVersionByUserIdAndProjectId(Long userId, Long projectId);

    List<Submission> findByStudentNameAndProjectIdOrderByVersionDesc(String studentName, Long projectId);

    List<Submission> findByUserIdAndProjectIdOrderByUploadedAtDesc(Long userId, Long projectId);

    @Query("SELECT s FROM Submission s WHERE s.project.id IN :projectIds AND s.version = " +
            "(SELECT MAX(s2.version) FROM Submission s2 WHERE s2.studentName = s.studentName AND s2.project.id = s.project.id) " +
            "ORDER BY s.uploadedAt DESC")
    List<Submission> findLatestPerStudentByProjectIds(List<Long> projectIds);

    // Task-scoped queries
    @Query("SELECT COALESCE(MAX(s.version), 0) FROM Submission s WHERE s.user.id = :userId AND s.task.id = :taskId")
    int findMaxVersionByUserIdAndTaskId(Long userId, Long taskId);

    @Query("SELECT s FROM Submission s WHERE s.project.id = :projectId AND s.version = " +
            "(SELECT MAX(s2.version) FROM Submission s2 WHERE s2.studentName = s.studentName " +
            "AND s2.project.id = :projectId AND (s2.task.id = s.task.id OR (s2.task IS NULL AND s.task IS NULL))) " +
            "ORDER BY s.uploadedAt DESC")
    List<Submission> findLatestPerStudentTaskByProjectId(Long projectId);

    @Query("SELECT s FROM Submission s WHERE s.project.id IN :projectIds AND s.version = " +
            "(SELECT MAX(s2.version) FROM Submission s2 WHERE s2.studentName = s.studentName " +
            "AND s2.project.id = s.project.id AND (s2.task.id = s.task.id OR (s2.task IS NULL AND s.task IS NULL))) " +
            "ORDER BY s.uploadedAt DESC")
    List<Submission> findLatestPerStudentTaskByProjectIds(List<Long> projectIds);

    List<Submission> findByStudentNameAndProjectIdAndTaskIdOrderByVersionDesc(String studentName, Long projectId, Long taskId);

    List<Submission> findByTaskIdOrderByUploadedAtAsc(Long taskId);

    List<Submission> findByTaskId(Long taskId);

    @Query("SELECT COALESCE(MAX(s.version), 0) FROM Submission s WHERE s.group.id = :groupId AND s.task.id = :taskId")
    int findMaxVersionByGroupIdAndTaskId(Long groupId, Long taskId);

    @Query("SELECT COALESCE(MAX(s.version), 0) FROM Submission s WHERE s.studentName = :studentName AND s.task.id = :taskId")
    int findMaxVersionByStudentNameAndTaskId(String studentName, Long taskId);

    @Query("SELECT COALESCE(MAX(s.version), 0) FROM Submission s WHERE s.studentName = :studentName AND s.project.id = :projectId AND s.task IS NULL")
    int findMaxVersionByStudentNameAndProjectIdNoTask(String studentName, Long projectId);
}
