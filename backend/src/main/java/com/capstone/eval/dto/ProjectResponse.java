package com.capstone.eval.dto;

import com.capstone.eval.model.Project;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String academicYear,
        String semester,
        String description,
        LocalDateTime createdAt,
        int memberCount,
        Long rulePackageId,
        String rulePackageName
) {

    public static ProjectResponse fromEntity(Project project, int memberCount) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getAcademicYear(),
                project.getSemester(),
                project.getDescription(),
                project.getCreatedAt(),
                memberCount,
                project.getRulePackage() != null ? project.getRulePackage().getId() : null,
                project.getRulePackage() != null ? project.getRulePackage().getName() : null
        );
    }
}
