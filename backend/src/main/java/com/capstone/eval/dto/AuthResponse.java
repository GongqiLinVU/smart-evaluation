package com.capstone.eval.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
