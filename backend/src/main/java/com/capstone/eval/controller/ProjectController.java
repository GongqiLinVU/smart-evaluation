package com.capstone.eval.controller;

import com.capstone.eval.dto.*;
import com.capstone.eval.model.Project;
import com.capstone.eval.model.ProjectMember;
import com.capstone.eval.model.ProjectTask;
import com.capstone.eval.model.enums.Role;
import com.capstone.eval.security.AuthPrincipal;
import com.capstone.eval.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.createProject(request, principal.userId(),
                Role.valueOf(principal.role()));
        int count = projectService.getMemberCount(project.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectResponse.fromEntity(project, count));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.updateProject(id, request);
        int count = projectService.getMemberCount(project.getId());
        return ResponseEntity.ok(ProjectResponse.fromEntity(project, count));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        List<Project> projects;
        if ("ADMIN".equals(principal.role())) {
            projects = projectService.getAllProjects();
        } else {
            projects = projectService.getProjectsForUser(principal.userId());
        }
        List<ProjectResponse> responses = projects.stream()
                .map(p -> ProjectResponse.fromEntity(p, projectService.getMemberCount(p.getId())))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable Long id) {
        Project project = projectService.getProject(id);
        int count = projectService.getMemberCount(project.getId());
        return ResponseEntity.ok(ProjectResponse.fromEntity(project, count));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable Long id,
                                                            @Valid @RequestBody ProjectMemberRequest request) {
        Role role = Role.valueOf(request.role().toUpperCase());
        ProjectMember member = projectService.addMember(id, request.userId(), role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectMemberResponse.fromEntity(member));
    }

    @PostMapping("/{id}/members/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<ProjectMemberResponse>> addMembersBulk(
            @PathVariable Long id,
            @RequestBody List<@Valid ProjectMemberRequest> requests) {
        List<ProjectMemberResponse> responses = requests.stream()
                .map(req -> {
                    Role role = Role.valueOf(req.role().toUpperCase());
                    ProjectMember member = projectService.addMember(id, req.userId(), role);
                    return ProjectMemberResponse.fromEntity(member);
                })
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(@PathVariable Long id) {
        List<ProjectMember> members = projectService.getMembers(id);
        List<ProjectMemberResponse> responses = members.stream()
                .map(ProjectMemberResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // Task endpoints

    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<ProjectTaskResponse>> listTasks(@PathVariable Long id) {
        List<ProjectTask> tasks = projectService.getTasks(id);
        List<ProjectTaskResponse> responses = tasks.stream()
                .map(ProjectTaskResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<ProjectTaskResponse> createTask(@PathVariable Long id,
                                                           @Valid @RequestBody CreateProjectTaskRequest request) {
        ProjectTask task = projectService.createTask(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectTaskResponse.fromEntity(task));
    }

    @PutMapping("/{projectId}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<ProjectTaskResponse> updateTask(@PathVariable Long projectId,
                                                           @PathVariable Long taskId,
                                                           @Valid @RequestBody CreateProjectTaskRequest request) {
        ProjectTask task = projectService.updateTask(taskId, request);
        return ResponseEntity.ok(ProjectTaskResponse.fromEntity(task));
    }

    @DeleteMapping("/{projectId}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<Void> deleteTask(@PathVariable Long projectId, @PathVariable Long taskId) {
        projectService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }
}
