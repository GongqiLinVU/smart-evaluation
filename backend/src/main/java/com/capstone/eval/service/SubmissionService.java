package com.capstone.eval.service;

import com.capstone.eval.config.FileStorageConfig;
import com.capstone.eval.exception.DocumentParseException;
import com.capstone.eval.model.*;
import com.capstone.eval.model.enums.EvaluationStatus;
import com.capstone.eval.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FileStorageConfig fileStorageConfig;

    public SubmissionService(SubmissionRepository submissionRepository,
                             UserRepository userRepository,
                             ProjectRepository projectRepository,
                             ProjectTaskRepository projectTaskRepository,
                             GroupMemberRepository groupMemberRepository,
                             FileStorageConfig fileStorageConfig) {
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectTaskRepository = projectTaskRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.fileStorageConfig = fileStorageConfig;
    }

    public Submission uploadSubmission(MultipartFile file, String studentName, String githubUrl, Long userId, Long projectId) {
        return uploadSubmission(file, studentName, githubUrl, userId, projectId, null);
    }

    public Submission uploadSubmission(MultipartFile file, String studentName, String githubUrl, Long userId, Long projectId, Long taskId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".docx")) {
            throw new DocumentParseException("Only .docx files are accepted. Received: " + originalFilename);
        }

        String uniqueFilename = UUID.randomUUID() + "_" + originalFilename;
        Path targetPath = Paths.get(fileStorageConfig.getUploadDir()).resolve(uniqueFilename);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DocumentParseException("Failed to store file: " + e.getMessage());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocumentParseException("User not found"));

        Project project = null;
        ProjectTask task = null;
        int nextVersion;

        if (taskId != null) {
            task = projectTaskRepository.findById(taskId)
                    .orElseThrow(() -> new DocumentParseException("Task not found"));
            project = task.getProject();
            nextVersion = submissionRepository.findMaxVersionByUserIdAndTaskId(userId, taskId) + 1;
        } else if (projectId != null) {
            project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new DocumentParseException("Project not found"));
            nextVersion = submissionRepository.findMaxVersionByUserIdAndProjectId(userId, projectId) + 1;
        } else {
            nextVersion = submissionRepository.findMaxVersionByUserId(userId) + 1;
        }

        Group linkedGroup = resolveGroupForUser(user, project);

        Submission submission = Submission.builder()
                .user(user)
                .project(project)
                .task(task)
                .group(linkedGroup)
                .studentName(studentName)
                .fileName(originalFilename)
                .filePath(targetPath.toString())
                .fileSizeBytes(file.getSize())
                .githubUrl(githubUrl)
                .version(nextVersion)
                .status(EvaluationStatus.PENDING)
                .build();

        return submissionRepository.save(submission);
    }

    public List<Submission> getAllSubmissions() {
        return submissionRepository.findAllByOrderByUploadedAtDesc();
    }

    public List<Submission> getLatestSubmissions() {
        return submissionRepository.findLatestPerStudent();
    }

    public List<Submission> getSubmissionsByUser(Long userId) {
        return submissionRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    public List<Submission> getSubmissionsByStudentName(String studentName) {
        return submissionRepository.findByStudentNameOrderByVersionDesc(studentName);
    }

    public Submission getSubmission(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new DocumentParseException("Submission not found with id: " + id));
    }

    public List<Submission> getLatestSubmissionsByProject(Long projectId) {
        return submissionRepository.findLatestPerStudentTaskByProjectId(projectId);
    }

    public List<Submission> getLatestSubmissionsByProjects(List<Long> projectIds) {
        if (projectIds.isEmpty()) return List.of();
        return submissionRepository.findLatestPerStudentTaskByProjectIds(projectIds);
    }

    public List<Submission> getAllSubmissionsByProject(Long projectId) {
        return submissionRepository.findByProjectIdOrderByUploadedAtDesc(projectId);
    }

    public List<Submission> getSubmissionsByStudentNameAndProject(String studentName, Long projectId) {
        return submissionRepository.findByStudentNameAndProjectIdOrderByVersionDesc(studentName, projectId);
    }

    public List<Submission> getSubmissionsByUserAndProject(Long userId, Long projectId) {
        return submissionRepository.findByUserIdAndProjectIdOrderByUploadedAtDesc(userId, projectId);
    }

    public List<Submission> getSubmissionsByStudentNameAndProjectAndTask(String studentName, Long projectId, Long taskId) {
        return submissionRepository.findByStudentNameAndProjectIdAndTaskIdOrderByVersionDesc(studentName, projectId, taskId);
    }

    private Group resolveGroupForUser(User user, Project project) {
        if (project == null) return null;

        // Match by user ID (if previously linked)
        return groupMemberRepository.findByUserIdAndProjectId(user.getId(), project.getId())
                .map(GroupMember::getGroup)
                // Match by email
                .or(() -> groupMemberRepository.findByEmailAndProjectId(user.getEmail(), project.getId())
                        .map(gm -> {
                            gm.setUser(user);
                            groupMemberRepository.save(gm);
                            return gm.getGroup();
                        }))
                // Match by studentId = email prefix (e.g., s12345@student.vu.edu.au → "s12345")
                .or(() -> {
                    String emailPrefix = user.getEmail().contains("@")
                            ? user.getEmail().substring(0, user.getEmail().indexOf('@'))
                            : user.getEmail();
                    return groupMemberRepository.findByStudentIdAndProjectId(emailPrefix, project.getId())
                            .map(gm -> {
                                gm.setUser(user);
                                gm.setEmail(user.getEmail());
                                groupMemberRepository.save(gm);
                                return gm.getGroup();
                            });
                })
                .orElse(null);
    }
}
