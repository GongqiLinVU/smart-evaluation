package com.capstone.eval.dto;

import com.capstone.eval.model.ProjectMember;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String role,
        LocalDateTime joinedAt
) {

    public static ProjectMemberResponse fromEntity(ProjectMember pm) {
        return new ProjectMemberResponse(
                pm.getId(),
                pm.getUser().getId(),
                pm.getUser().getFullName(),
                pm.getUser().getEmail(),
                pm.getRole().name(),
                pm.getJoinedAt()
        );
    }
}
