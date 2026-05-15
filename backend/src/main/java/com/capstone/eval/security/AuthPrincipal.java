package com.capstone.eval.security;

public record AuthPrincipal(Long userId, String email, String role) {
}
