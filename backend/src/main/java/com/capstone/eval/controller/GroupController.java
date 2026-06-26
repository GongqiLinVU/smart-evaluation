package com.capstone.eval.controller;

import com.capstone.eval.dto.GroupRequest;
import com.capstone.eval.dto.GroupResponse;
import com.capstone.eval.dto.GroupSubmissionInfoResponse;
import com.capstone.eval.model.Group;
import com.capstone.eval.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
public class GroupController {

    private final GroupService groupService;

    @GetMapping("/projects/{projectId}/groups")
    public ResponseEntity<List<GroupResponse>> listGroups(@PathVariable Long projectId) {
        List<GroupResponse> groups = groupService.listByProject(projectId).stream()
                .map(GroupResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(groups);
    }

    @PostMapping("/projects/{projectId}/groups")
    public ResponseEntity<GroupResponse> createGroup(@PathVariable Long projectId, @RequestBody GroupRequest request) {
        Group group = groupService.create(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupResponse.fromEntity(group));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable Long id, @RequestBody GroupRequest request) {
        Group group = groupService.update(id, request);
        return ResponseEntity.ok(GroupResponse.fromEntity(group));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/groups/import")
    public ResponseEntity<List<GroupResponse>> importCsv(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        List<Group> created = groupService.importCsv(projectId, file.getInputStream());
        List<GroupResponse> responses = groupService.listByProject(projectId).stream()
                .map(GroupResponse::fromEntity)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/tasks/{taskId}/group-submissions")
    public ResponseEntity<List<GroupSubmissionInfoResponse>> getGroupSubmissions(@PathVariable Long taskId) {
        List<GroupSubmissionInfoResponse> infos = groupService.getGroupSubmissionInfos(taskId);
        return ResponseEntity.ok(infos);
    }
}
