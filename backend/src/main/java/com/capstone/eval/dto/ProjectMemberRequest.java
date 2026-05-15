package com.capstone.eval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProjectMemberRequest(
        @NotNull Long userId,
        @NotBlank String role
) {
}
