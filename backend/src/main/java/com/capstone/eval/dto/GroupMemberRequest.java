package com.capstone.eval.dto;

public record GroupMemberRequest(
        String studentName,
        String studentId,
        String email,
        Double contributionPercent
) {}
