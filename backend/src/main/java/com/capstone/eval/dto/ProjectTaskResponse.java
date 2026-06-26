package com.capstone.eval.dto;

import com.capstone.eval.model.ProjectTask;

import java.time.LocalDateTime;

public record ProjectTaskResponse(
        Long id,
        Long projectId,
        String name,
        String description,
        Integer displayOrder,
        Long rulePackageId,
        String rulePackageName,
        Long llmConfigId,
        String llmConfigName,
        LocalDateTime createdAt
) {

    public static ProjectTaskResponse fromEntity(ProjectTask task) {
        return new ProjectTaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getName(),
                task.getDescription(),
                task.getDisplayOrder(),
                task.getRulePackage() != null ? task.getRulePackage().getId() : null,
                task.getRulePackage() != null ? task.getRulePackage().getName() : null,
                task.getLlmConfig() != null ? task.getLlmConfig().getId() : null,
                task.getLlmConfig() != null ? task.getLlmConfig().getName() : null,
                task.getCreatedAt()
        );
    }
}
