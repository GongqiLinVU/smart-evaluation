package com.capstone.eval.dto;

import com.capstone.eval.model.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        String academicYear,
        String semester,
        LocalDateTime createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getAcademicYear(),
                user.getSemester(),
                user.getCreatedAt()
        );
    }
}
