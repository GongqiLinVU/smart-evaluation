package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String fullName,
        String currentPassword,
        String newPassword
) {
}
