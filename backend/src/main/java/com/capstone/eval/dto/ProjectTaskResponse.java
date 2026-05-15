package com.capstone.eval.dto;

import com.capstone.eval.model.ProjectTask;

import java.time.LocalDateTime;

public record ProjectTaskResponse(
        Long id,
        Long projectId,
        String name,
        String description,
        Integer displayOrder,
        LocalDateTime createdAt
) {

    public static ProjectTaskResponse fromEntity(ProjectTask task) {
        return new ProjectTaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getName(),
                task.getDescription(),
                task.getDisplayOrder(),
                task.getCreatedAt()
        );
    }
}
