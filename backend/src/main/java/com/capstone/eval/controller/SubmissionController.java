package com.capstone.eval.controller;

import com.capstone.eval.dto.*;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationVisibility;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.ParsedDocumentRepository;
import com.capstone.eval.security.AuthPrincipal;
import com.capstone.eval.service.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final EvaluationResultRepository evaluationResultRepository;
    private final ParsedDocumentRepository parsedDocumentRepository;
    private final com.capstone.eval.repository.SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;
    private final com.capstone.eval.service.ProjectService projectService;

    @PostMapping("/upload")
    public ResponseEntity<SubmissionResponse> upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam("studentName") String studentName,
            @RequestParam(value = "githubUrl", required = false) String githubUrl,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "taskId", required = false) Long taskId) {

        Submission submission = submissionService.uploadSubmission(
                file, studentName, githubUrl, principal.userId(), projectId, taskId);

        SubmissionResponse response = SubmissionResponse.fromEntity(submission, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<SubmissionResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        List<Submission> submissions = resolveSubmissions(principal, projectId, false);
        return ResponseEntity.ok(toResponseList(submissions));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<SubmissionResponse>> listLatest(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        List<Submission> submissions = resolveSubmissions(principal, projectId, true);
        return ResponseEntity.ok(toResponseList(submissions));
    }

    @GetMapping("/versions/{studentName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    public ResponseEntity<List<SubmissionResponse>> getVersions(
            @PathVariable String studentName,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "taskId", required = false) Long taskId) {
        List<Submission> submissions;
        if (projectId != null && taskId != null) {
            submissions = submissionService.getSubmissionsByStudentNameAndProjectAndTask(studentName, projectId, taskId);
        } else if (projectId != null) {
            submissions = submissionService.getSubmissionsByStudentNameAndProject(studentName, projectId);
        } else {
            submissions = submissionService.getSubmissionsByStudentName(studentName);
        }
        int total = submissions.size();
        List<SubmissionResponse> responses = submissions.stream()
                .map(sub -> {
                    EvaluationResult latestEval = findLatestEvaluation(sub.getId());
                    return SubmissionResponse.fromEntity(sub, latestEval, total);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/my")
    public ResponseEntity<List<SubmissionResponse>> mySubmissions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        List<Submission> submissions = projectId != null
                ? submissionService.getSubmissionsByUserAndProject(principal.userId(), projectId)
                : submissionService.getSubmissionsByUser(principal.userId());
        return ResponseEntity.ok(toResponseList(submissions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Submission submission = submissionService.getSubmission(id);

        boolean isPrivileged = "ADMIN".equals(principal.role()) || "TUTOR".equals(principal.role());

        EvaluationResult latestEval = findLatestEvaluation(id, isPrivileged);
        SubmissionResponse submissionResponse = SubmissionResponse.fromEntity(submission, latestEval);

        DocumentStatsResponse documentStats = parsedDocumentRepository.findBySubmissionId(id)
                .map(DocumentStatsResponse::fromEntity)
                .orElse(null);

        List<EvaluationResultResponse> evaluations = evaluationResultRepository
                .findBySubmissionId(id)
                .stream()
                .filter(er -> isPrivileged || er.getVisibility() != EvaluationVisibility.INTERNAL)
                .map(er -> EvaluationResultResponse.fromEntity(er, objectMapper))
                .collect(Collectors.toList());

        SubmissionDetailResponse detail = new SubmissionDetailResponse(
                submissionResponse, documentStats, evaluations);

        return ResponseEntity.ok(detail);
    }

    private List<Submission> resolveSubmissions(AuthPrincipal principal, Long projectId, boolean latestOnly) {
        if (projectId != null) {
            return latestOnly
                    ? submissionService.getLatestSubmissionsByProject(projectId)
                    : submissionService.getAllSubmissionsByProject(projectId);
        }
        if ("TUTOR".equals(principal.role())) {
            List<Long> projectIds = projectService.getProjectIdsForUser(principal.userId());
            if (projectIds.isEmpty()) return List.of();
            return latestOnly
                    ? submissionService.getLatestSubmissionsByProjects(projectIds)
                    : submissionService.getAllSubmissions(); // fallback for non-latest
        }
        return latestOnly
                ? submissionService.getLatestSubmissions()
                : submissionService.getAllSubmissions();
    }

    private List<SubmissionResponse> toResponseList(List<Submission> submissions) {
        return submissions.stream()
                .map(sub -> {
                    EvaluationResult latestEval = findLatestEvaluation(sub.getId());
                    int totalVersions = computeTotalVersions(sub);
                    return SubmissionResponse.fromEntity(sub, latestEval, totalVersions);
                })
                .collect(Collectors.toList());
    }

    private int computeTotalVersions(Submission sub) {
        if (sub.getTask() != null) {
            return submissionRepository.findMaxVersionByStudentNameAndTaskId(
                    sub.getStudentName(), sub.getTask().getId());
        }
        if (sub.getProject() != null) {
            return submissionRepository.findMaxVersionByStudentNameAndProjectIdNoTask(
                    sub.getStudentName(), sub.getProject().getId());
        }
        return sub.getVersion();
    }

    private EvaluationResult findLatestEvaluation(Long submissionId) {
        return findLatestEvaluation(submissionId, true);
    }

    private EvaluationResult findLatestEvaluation(Long submissionId, boolean includeInternal) {
        List<EvaluationResult> evaluations = evaluationResultRepository.findBySubmissionId(submissionId);
        if (evaluations.isEmpty()) {
            return null;
        }
        return evaluations.stream()
                .filter(er -> includeInternal || er.getVisibility() != EvaluationVisibility.INTERNAL)
                .max(Comparator.comparing(EvaluationResult::getEvaluatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }
}
