package com.capstone.eval.service;

import com.capstone.eval.dto.CreateProjectRequest;
import com.capstone.eval.dto.CreateProjectTaskRequest;
import com.capstone.eval.model.Project;
import com.capstone.eval.model.ProjectMember;
import com.capstone.eval.model.ProjectTask;
import com.capstone.eval.model.RulePackage;
import com.capstone.eval.model.User;
import com.capstone.eval.model.enums.Role;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.EvaluationRoundRepository;
import com.capstone.eval.repository.GroupMemberRepository;
import com.capstone.eval.repository.GroupRepository;
import com.capstone.eval.repository.LlmConfigRepository;
import com.capstone.eval.repository.ParsedDocumentRepository;
import com.capstone.eval.repository.ProjectMemberRepository;
import com.capstone.eval.repository.ProjectRepository;
import com.capstone.eval.repository.ProjectTaskRepository;
import com.capstone.eval.repository.RulePackageRepository;
import com.capstone.eval.repository.ScoreAdjustmentRepository;
import com.capstone.eval.repository.SubmissionRepository;
import com.capstone.eval.repository.TutorReviewRepository;
import com.capstone.eval.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final RulePackageRepository rulePackageRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final EvaluationRoundRepository evaluationRoundRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final TutorReviewRepository tutorReviewRepository;
    private final ParsedDocumentRepository parsedDocumentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    public Project createProject(CreateProjectRequest request) {
        return createProject(request, null, null);
    }

    public Project createProject(CreateProjectRequest request, Long creatorId, Role creatorRole) {
        RulePackage rulePackage = null;
        if (request.rulePackageId() != null) {
            rulePackage = rulePackageRepository.findById(request.rulePackageId())
                    .orElseThrow(() -> new RuntimeException("Rule package not found: " + request.rulePackageId()));
        }

        Project project = Project.builder()
                .name(request.name())
                .academicYear(request.academicYear())
                .semester(request.semester())
                .description(request.description())
                .rulePackage(rulePackage)
                .build();
        project = projectRepository.save(project);

        if (creatorId != null && creatorRole != null) {
            User creator = userRepository.findById(creatorId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + creatorId));
            ProjectMember member = ProjectMember.builder()
                    .project(project)
                    .user(creator)
                    .role(creatorRole)
                    .build();
            projectMemberRepository.save(member);
        }

        return project;
    }

    public Project updateProject(Long id, CreateProjectRequest request) {
        Project project = getProject(id);
        project.setName(request.name());
        project.setAcademicYear(request.academicYear());
        project.setSemester(request.semester());
        project.setDescription(request.description());

        if (request.rulePackageId() != null) {
            RulePackage rulePackage = rulePackageRepository.findById(request.rulePackageId())
                    .orElseThrow(() -> new RuntimeException("Rule package not found: " + request.rulePackageId()));
            project.setRulePackage(rulePackage);
        } else {
            project.setRulePackage(null);
        }

        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        // Delete submission-linked data first (deepest level)
        var submissions = submissionRepository.findByProjectIdOrderByUploadedAtDesc(id);
        for (var submission : submissions) {
            Long sid = submission.getId();
            var evalResults = evaluationResultRepository.findBySubmissionId(sid);
            for (var result : evalResults) {
                evaluationRoundRepository.deleteByEvaluationResultId(result.getId());
                scoreAdjustmentRepository.deleteByEvaluationId(result.getId());
            }
            evaluationResultRepository.deleteAll(evalResults);
            tutorReviewRepository.deleteBySubmissionId(sid);
            parsedDocumentRepository.deleteBySubmissionId(sid);
        }
        submissionRepository.deleteAll(submissions);

        // Delete groups and their members
        var groups = groupRepository.findByProjectIdOrderByGroupCodeAsc(id);
        for (var group : groups) {
            groupMemberRepository.deleteByGroupId(group.getId());
        }
        groupRepository.deleteByProjectId(id);

        projectMemberRepository.deleteByProjectId(id);
        projectTaskRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));
    }

    public List<Project> getProjectsForUser(Long userId) {
        return projectMemberRepository.findByUserId(userId).stream()
                .map(ProjectMember::getProject)
                .toList();
    }

    public List<Long> getProjectIdsForUser(Long userId) {
        return projectMemberRepository.findByUserId(userId).stream()
                .map(pm -> pm.getProject().getId())
                .toList();
    }

    public ProjectMember addMember(Long projectId, Long userId, Role role) {
        Project project = getProject(projectId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        var existing = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .build();
        return projectMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    public List<ProjectMember> getMembers(Long projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    public int getMemberCount(Long projectId) {
        return projectMemberRepository.countByProjectId(projectId);
    }

    // Task management

    public List<ProjectTask> getTasks(Long projectId) {
        return projectTaskRepository.findByProjectIdOrderByDisplayOrderAsc(projectId);
    }

    public ProjectTask getTask(Long taskId) {
        return projectTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
    }

    public ProjectTask createTask(Long projectId, CreateProjectTaskRequest request) {
        Project project = getProject(projectId);
        int order = request.displayOrder() != null
                ? request.displayOrder()
                : projectTaskRepository.countByProjectId(projectId);

        RulePackage rulePackage = null;
        if (request.rulePackageId() != null) {
            rulePackage = rulePackageRepository.findById(request.rulePackageId())
                    .orElseThrow(() -> new RuntimeException("Rule package not found: " + request.rulePackageId()));
        }

        LlmConfig llmConfig = null;
        if (request.llmConfigId() != null) {
            llmConfig = llmConfigRepository.findById(request.llmConfigId())
                    .orElseThrow(() -> new RuntimeException("LLM config not found: " + request.llmConfigId()));
        }

        ProjectTask task = ProjectTask.builder()
                .project(project)
                .name(request.name())
                .description(request.description())
                .displayOrder(order)
                .rulePackage(rulePackage)
                .llmConfig(llmConfig)
                .build();
        return projectTaskRepository.save(task);
    }

    public ProjectTask updateTask(Long taskId, CreateProjectTaskRequest request) {
        ProjectTask task = getTask(taskId);
        task.setName(request.name());
        task.setDescription(request.description());
        if (request.displayOrder() != null) {
            task.setDisplayOrder(request.displayOrder());
        }

        if (request.rulePackageId() != null) {
            RulePackage rulePackage = rulePackageRepository.findById(request.rulePackageId())
                    .orElseThrow(() -> new RuntimeException("Rule package not found: " + request.rulePackageId()));
            task.setRulePackage(rulePackage);
        } else {
            task.setRulePackage(null);
        }

        if (request.llmConfigId() != null) {
            LlmConfig llmConfig = llmConfigRepository.findById(request.llmConfigId())
                    .orElseThrow(() -> new RuntimeException("LLM config not found: " + request.llmConfigId()));
            task.setLlmConfig(llmConfig);
        } else {
            task.setLlmConfig(null);
        }

        return projectTaskRepository.save(task);
    }

    public void deleteTask(Long taskId) {
        projectTaskRepository.deleteById(taskId);
    }
}
