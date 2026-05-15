package com.capstone.eval.controller;

import com.capstone.eval.dto.*;
import com.capstone.eval.model.User;
import com.capstone.eval.model.enums.Role;
import com.capstone.eval.repository.UserRepository;
import com.capstone.eval.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(request.fullName());

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.currentPassword() == null ||
                    !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                return ResponseEntity.badRequest().build();
            }
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }

        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        List<User> users;
        if (academicYear != null && semester != null) {
            users = userRepository.findByRoleAndAcademicYearAndSemester(
                    Role.STUDENT, academicYear, semester);
        } else {
            users = userRepository.findAll();
        }
        List<UserResponse> responses = users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role newRole;
        try {
            newRole = Role.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        user.setRole(newRole);
        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @PutMapping("/{id}/academic-info")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateAcademicInfo(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (body.containsKey("academicYear")) {
            user.setAcademicYear(body.get("academicYear"));
        }
        if (body.containsKey("semester")) {
            user.setSemester(body.get("semester"));
        }

        user = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }
}
