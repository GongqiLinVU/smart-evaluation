package com.capstone.eval.service;

import com.capstone.eval.config.FileStorageConfig;
import com.capstone.eval.exception.DocumentParseException;
import com.capstone.eval.model.Project;
import com.capstone.eval.model.ProjectTask;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.User;
import com.capstone.eval.model.enums.EvaluationStatus;
import com.capstone.eval.repository.ProjectRepository;
import com.capstone.eval.repository.ProjectTaskRepository;
import com.capstone.eval.repository.SubmissionRepository;
import com.capstone.eval.repository.UserRepository;
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
    private final FileStorageConfig fileStorageConfig;

    public SubmissionService(SubmissionRepository submissionRepository,
                             UserRepository userRepository,
                             ProjectRepository projectRepository,
                             ProjectTaskRepository projectTaskRepository,
                             FileStorageConfig fileStorageConfig) {
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectTaskRepository = projectTaskRepository;
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

        Submission submission = Submission.builder()
                .user(user)
                .project(project)
                .task(task)
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
}
