package com.capstone.eval.service;

import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.Submission;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.EvaluationStatus;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchEvaluationService {

    private final SubmissionRepository submissionRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final EvaluationOrchestrator evaluationOrchestrator;

    private final Map<Long, BatchStatus> batchStatuses = new ConcurrentHashMap<>();

    public record BatchStatus(
            Long taskId,
            int total,
            int completed,
            int failed,
            String status,
            List<ItemStatus> items
    ) {}

    public record ItemStatus(
            Long submissionId,
            String groupCode,
            String status,
            Integer overallScore,
            String error
    ) {}

    public BatchStatus getStatus(Long taskId) {
        return batchStatuses.get(taskId);
    }

    @Async
    public void startBatchEvaluation(Long taskId, EvaluationMethod method) {
        List<Submission> submissions = submissionRepository.findByTaskIdOrderByUploadedAtAsc(taskId);
        List<Submission> deduped = deduplicateSubmissions(submissions);

        List<Submission> pending = deduped.stream()
                .filter(s -> s.getStatus() == EvaluationStatus.PENDING
                        || s.getStatus() == EvaluationStatus.FAILED)
                .toList();

        if (pending.isEmpty()) {
            batchStatuses.put(taskId, new BatchStatus(taskId, 0, 0, 0, "COMPLETED", List.of()));
            return;
        }

        List<ItemStatus> items = new java.util.ArrayList<>(pending.stream()
                .map(s -> new ItemStatus(s.getId(), s.getStudentName(), "PENDING", null, null))
                .toList());

        batchStatuses.put(taskId, new BatchStatus(taskId, pending.size(), 0, 0, "IN_PROGRESS", items));

        int completed = 0;
        int failed = 0;

        for (int i = 0; i < pending.size(); i++) {
            Submission sub = pending.get(i);
            try {
                EvaluationResult result = evaluationOrchestrator.evaluateSubmission(sub.getId(), method);
                items.set(i, new ItemStatus(sub.getId(), sub.getStudentName(), "SUCCESS",
                        result.getOverallScore(), null));
                completed++;
            } catch (Exception e) {
                log.error("Batch evaluation failed for submission {}: {}", sub.getId(), e.getMessage());
                items.set(i, new ItemStatus(sub.getId(), sub.getStudentName(), "FAILED",
                        null, e.getMessage()));
                failed++;
            }
            batchStatuses.put(taskId, new BatchStatus(taskId, pending.size(), completed, failed, "IN_PROGRESS", items));

            if (i < pending.size() - 1) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        batchStatuses.put(taskId, new BatchStatus(taskId, pending.size(), completed, failed, "COMPLETED", items));
        log.info("Batch evaluation completed for task {}: {}/{} succeeded", taskId, completed, pending.size());
    }

    private List<Submission> deduplicateSubmissions(List<Submission> submissions) {
        Map<String, Submission> latest = new LinkedHashMap<>();
        for (Submission sub : submissions) {
            String key = sub.getGroup() != null
                    ? "group:" + sub.getGroup().getId()
                    : "user:" + (sub.getUser() != null ? sub.getUser().getId() : sub.getStudentName());
            Submission existing = latest.get(key);
            if (existing == null || sub.getUploadedAt().isAfter(existing.getUploadedAt())) {
                latest.put(key, sub);
            }
        }
        return new ArrayList<>(latest.values());
    }
}
