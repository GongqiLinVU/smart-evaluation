package com.capstone.eval.service;

import com.capstone.eval.dto.DocumentStatsResponse;
import com.capstone.eval.dto.GroupMemberRequest;
import com.capstone.eval.dto.GroupRequest;
import com.capstone.eval.dto.GroupSubmissionInfoResponse;
import com.capstone.eval.model.*;
import com.capstone.eval.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ParsedDocumentRepository parsedDocumentRepository;

    public List<Group> listByProject(Long projectId) {
        return groupRepository.findByProjectIdOrderByGroupCodeAsc(projectId);
    }

    public Group getById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
    }

    @Transactional
    public Group create(Long projectId, GroupRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (groupRepository.findByProjectIdAndGroupCode(projectId, request.groupCode()).isPresent()) {
            throw new IllegalArgumentException("Group code '" + request.groupCode() + "' already exists in this project");
        }

        Group group = Group.builder()
                .groupCode(request.groupCode())
                .groupName(request.groupName() != null ? request.groupName() : request.groupCode())
                .project(project)
                .build();

        Group saved = groupRepository.save(group);
        if (request.members() != null) {
            setMembers(saved, request.members());
        }
        linkExistingSubmissions(saved);
        return saved;
    }

    @Transactional
    public Group update(Long groupId, GroupRequest request) {
        Group group = getById(groupId);
        group.setGroupCode(request.groupCode());
        group.setGroupName(request.groupName() != null ? request.groupName() : request.groupCode());

        if (request.members() != null) {
            group.getMembers().clear();
            setMembers(group, request.members());
        }
        Group saved = groupRepository.save(group);
        linkExistingSubmissions(saved);
        return saved;
    }

    @Transactional
    public void delete(Long groupId) {
        groupRepository.deleteById(groupId);
    }

    @Transactional
    public List<Group> importCsv(Long projectId, InputStream csvInput) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<Group> created = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInput))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 3) continue;

                String groupCode = parts[0].trim();
                String studentName = parts[1].trim();
                String studentId = parts[2].trim();
                Double contribution = parts.length > 3 && !parts[3].trim().isEmpty()
                        ? Double.parseDouble(parts[3].trim()) : null;
                String email = parts.length > 4 ? parts[4].trim() : null;

                Group group = groupRepository.findByProjectIdAndGroupCode(projectId, groupCode)
                        .orElseGet(() -> {
                            Group g = Group.builder()
                                    .groupCode(groupCode)
                                    .groupName(groupCode)
                                    .project(project)
                                    .build();
                            created.add(g);
                            return groupRepository.save(g);
                        });

                User linkedUser = resolveUser(email, studentId);
                GroupMember member = GroupMember.builder()
                        .group(group)
                        .studentName(studentName)
                        .studentId(studentId)
                        .email(email)
                        .user(linkedUser)
                        .contributionPercent(contribution)
                        .build();
                group.getMembers().add(member);
                groupRepository.save(group);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV parse error: " + e.getMessage());
        }

        for (Group g : created) {
            linkExistingSubmissions(g);
        }
        // Also link for existing groups that got new members
        List<Group> allProjectGroups = groupRepository.findByProjectIdOrderByGroupCodeAsc(projectId);
        for (Group g : allProjectGroups) {
            if (!created.contains(g)) {
                linkExistingSubmissions(g);
            }
        }
        return created;
    }

    public Group findOrCreate(Long projectId, String groupCode, Project project) {
        return groupRepository.findByProjectIdAndGroupCode(projectId, groupCode)
                .orElseGet(() -> {
                    Group g = Group.builder()
                            .groupCode(groupCode)
                            .groupName(groupCode)
                            .project(project)
                            .build();
                    return groupRepository.save(g);
                });
    }

    private void setMembers(Group group, List<GroupMemberRequest> memberRequests) {
        for (GroupMemberRequest mr : memberRequests) {
            User linkedUser = resolveUser(mr.email(), mr.studentId());
            GroupMember member = GroupMember.builder()
                    .group(group)
                    .studentName(mr.studentName())
                    .studentId(mr.studentId())
                    .email(mr.email())
                    .user(linkedUser)
                    .contributionPercent(mr.contributionPercent())
                    .build();
            group.getMembers().add(member);
        }
    }

    private User resolveUser(String email, String studentId) {
        if (email != null && !email.isBlank()) {
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent()) return user.get();
        }
        if (studentId != null && !studentId.isBlank()) {
            List<User> candidates = userRepository.findAll().stream()
                    .filter(u -> u.getEmail().startsWith(studentId + "@"))
                    .toList();
            if (candidates.size() == 1) return candidates.get(0);
        }
        return null;
    }

    private void linkExistingSubmissions(Group group) {
        Long projectId = group.getProject().getId();
        for (GroupMember member : group.getMembers()) {
            User user = member.getUser();
            if (user == null) continue;
            List<Submission> unlinked = submissionRepository.findByProjectIdOrderByUploadedAtDesc(projectId)
                    .stream()
                    .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                    .filter(s -> s.getGroup() == null)
                    .toList();
            for (Submission sub : unlinked) {
                sub.setGroup(group);
                submissionRepository.save(sub);
                log.info("Retroactively linked submission {} to group {}", sub.getId(), group.getGroupCode());
            }
        }
    }

    private static final int IMAGE_HEAVY_THRESHOLD = 5;

    @Transactional(readOnly = true)
    public List<GroupSubmissionInfoResponse> getGroupSubmissionInfos(Long taskId) {
        List<Submission> submissions = submissionRepository.findByTaskIdOrderByUploadedAtAsc(taskId);

        Map<Long, Submission> latestPerGroup = new LinkedHashMap<>();
        for (Submission sub : submissions) {
            if (sub.getGroup() == null) continue;
            Long gid = sub.getGroup().getId();
            Submission existing = latestPerGroup.get(gid);
            if (existing == null || sub.getUploadedAt().isAfter(existing.getUploadedAt())) {
                latestPerGroup.put(gid, sub);
            }
        }

        List<GroupSubmissionInfoResponse> result = new ArrayList<>();
        for (Submission sub : latestPerGroup.values()) {
            Optional<ParsedDocumentEntity> parsed = parsedDocumentRepository.findBySubmissionId(sub.getId());
            DocumentStatsResponse stats = parsed.map(DocumentStatsResponse::fromEntity).orElse(null);
            boolean imageHeavy = parsed.map(p -> p.getImageCount() != null && p.getImageCount() >= IMAGE_HEAVY_THRESHOLD)
                    .orElse(false);

            result.add(new GroupSubmissionInfoResponse(
                    sub.getGroup().getId(),
                    sub.getGroup().getGroupCode(),
                    sub.getId(),
                    sub.getFileName(),
                    sub.getFileSizeBytes(),
                    sub.getStatus().name(),
                    sub.getUploadedAt(),
                    stats,
                    imageHeavy
            ));
        }
        return result;
    }
}
