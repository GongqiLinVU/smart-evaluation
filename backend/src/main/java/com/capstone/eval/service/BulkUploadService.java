package com.capstone.eval.service;

import com.capstone.eval.config.FileStorageConfig;
import com.capstone.eval.dto.BulkUploadResponse;
import com.capstone.eval.model.Group;
import com.capstone.eval.model.Project;
import com.capstone.eval.model.ProjectTask;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationStatus;
import com.capstone.eval.repository.GroupRepository;
import com.capstone.eval.repository.ProjectTaskRepository;
import com.capstone.eval.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkUploadService {

    private final ProjectTaskRepository projectTaskRepository;
    private final GroupRepository groupRepository;
    private final SubmissionRepository submissionRepository;
    private final GroupService groupService;
    private final FileStorageConfig fileStorageConfig;
    private final DocumentParserService documentParserService;

    private static final Pattern GROUP_PATTERN = Pattern.compile(
            "^(Group|Team|group|team)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PREFIX_PATTERN = Pattern.compile("^(\\d+)_");

    @Transactional
    public BulkUploadResponse bulkUpload(Long taskId, List<MultipartFile> files) {
        ProjectTask task = projectTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Project project = task.getProject();

        int groupsCreated = 0;
        int groupsMatched = 0;
        List<BulkUploadResponse.UploadedItem> uploaded = new ArrayList<>();
        List<BulkUploadResponse.UploadError> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                errors.add(new BulkUploadResponse.UploadError("(unknown)", "Empty filename"));
                continue;
            }

            String lower = originalFilename.toLowerCase();
            if (!lower.endsWith(".pdf") && !lower.endsWith(".docx") && !lower.endsWith(".doc")) {
                errors.add(new BulkUploadResponse.UploadError(originalFilename, "Unsupported file type (use .pdf, .docx, or .doc)"));
                continue;
            }

            String groupCode = parseGroupCode(originalFilename);

            boolean isNew = groupRepository.findByProjectIdAndGroupCode(project.getId(), groupCode).isEmpty();
            Group group = groupService.findOrCreate(project.getId(), groupCode, project);
            if (isNew) {
                groupsCreated++;
            } else {
                groupsMatched++;
            }

            try {
                String uniqueFilename = UUID.randomUUID() + "_" + originalFilename;
                Path targetPath = Paths.get(fileStorageConfig.getUploadDir()).resolve(uniqueFilename);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                int nextVersion = submissionRepository.findMaxVersionByGroupIdAndTaskId(
                        group.getId(), task.getId()) + 1;

                Submission submission = Submission.builder()
                        .project(project)
                        .task(task)
                        .group(group)
                        .studentName(groupCode)
                        .fileName(originalFilename)
                        .filePath(targetPath.toString())
                        .fileSizeBytes(file.getSize())
                        .version(nextVersion)
                        .status(EvaluationStatus.PENDING)
                        .build();

                Submission saved = submissionRepository.save(submission);
                uploaded.add(new BulkUploadResponse.UploadedItem(saved.getId(), groupCode, originalFilename));
                log.info("Bulk uploaded: {} -> group {}", originalFilename, groupCode);
            } catch (IOException e) {
                errors.add(new BulkUploadResponse.UploadError(originalFilename, "File storage failed: " + e.getMessage()));
            }
        }

        // Trigger async document parsing for all uploaded submissions
        List<Long> submissionIds = uploaded.stream()
                .map(BulkUploadResponse.UploadedItem::submissionId)
                .toList();
        parseDocumentsAsync(submissionIds);

        return new BulkUploadResponse(uploaded.size(), groupsCreated, groupsMatched, uploaded, errors);
    }

    @Async
    public void parseDocumentsAsync(List<Long> submissionIds) {
        for (Long submissionId : submissionIds) {
            try {
                Submission sub = submissionRepository.findById(submissionId).orElse(null);
                if (sub == null) continue;
                String fileName = sub.getFileName().toLowerCase();
                if (fileName.endsWith(".docx")) {
                    documentParserService.parseAndStore(sub);
                    log.info("Parsed document for bulk-uploaded submission id={}", submissionId);
                } else {
                    log.info("Skipping parsing for non-docx file: {} (submission id={})", sub.getFileName(), submissionId);
                }
            } catch (Exception e) {
                log.warn("Failed to parse document for submission id={}: {}", submissionId, e.getMessage());
            }
        }
    }

    String parseGroupCode(String filename) {
        String stem = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;

        Matcher groupMatcher = GROUP_PATTERN.matcher(stem);
        if (groupMatcher.find()) {
            return "Group" + String.format("%02d", Integer.parseInt(groupMatcher.group(2)));
        }

        Matcher digitMatcher = DIGIT_PREFIX_PATTERN.matcher(stem);
        if (digitMatcher.find()) {
            return "Group" + String.format("%02d", Integer.parseInt(digitMatcher.group(1)));
        }

        return stem.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
