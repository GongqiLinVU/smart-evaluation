package com.capstone.eval.service;

import com.capstone.eval.model.*;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.GroupRepository;
import com.capstone.eval.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final SubmissionRepository submissionRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final GroupRepository groupRepository;

    public String exportTaskResultsCsv(Long taskId) {
        List<Submission> allSubmissions = submissionRepository.findByTaskId(taskId);
        List<Submission> submissions = deduplicateSubmissions(allSubmissions);
        StringBuilder csv = new StringBuilder();
        csv.append("GroupCode,GroupName,StudentName,StudentID,ContributionPercent,OverallScore,Level,Confidence,Feedback\n");

        for (Submission sub : submissions) {
            EvaluationResult eval = evaluationResultRepository.findBySubmissionId(sub.getId())
                    .stream()
                    .max(Comparator.comparing(EvaluationResult::getEvaluatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);

            Group group = sub.getGroup();
            String groupCode = group != null ? group.getGroupCode() : "";
            String groupName = group != null ? (group.getGroupName() != null ? group.getGroupName() : "") : "";

            if (group != null && group.getMembers() != null && !group.getMembers().isEmpty()) {
                for (GroupMember member : group.getMembers()) {
                    csv.append(escapeCsv(groupCode)).append(',');
                    csv.append(escapeCsv(groupName)).append(',');
                    csv.append(escapeCsv(member.getStudentName())).append(',');
                    csv.append(escapeCsv(member.getStudentId() != null ? member.getStudentId() : "")).append(',');
                    csv.append(member.getContributionPercent() != null ? member.getContributionPercent() : "").append(',');
                    appendEvalColumns(csv, eval);
                    csv.append('\n');
                }
            } else {
                csv.append(escapeCsv(groupCode)).append(',');
                csv.append(escapeCsv(groupName)).append(',');
                csv.append(escapeCsv(sub.getStudentName())).append(',');
                csv.append(',');
                csv.append(',');
                appendEvalColumns(csv, eval);
                csv.append('\n');
            }
        }
        return csv.toString();
    }

    private void appendEvalColumns(StringBuilder csv, EvaluationResult eval) {
        if (eval != null) {
            csv.append(eval.getOverallScore() != null ? eval.getOverallScore() : "").append(',');
            csv.append(eval.getOverallLevel() != null ? eval.getOverallLevel() : "").append(',');
            csv.append(eval.getConfidence() != null ? String.format("%.2f", eval.getConfidence()) : "").append(',');
            csv.append(escapeCsv(eval.getOverallFeedback() != null ? eval.getOverallFeedback() : ""));
        } else {
            csv.append(",,,");
        }
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

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
