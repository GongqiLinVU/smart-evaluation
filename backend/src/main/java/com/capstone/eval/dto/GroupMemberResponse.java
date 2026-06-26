package com.capstone.eval.dto;

import com.capstone.eval.model.GroupMember;

public record GroupMemberResponse(
        Long id,
        String studentName,
        String studentId,
        String email,
        boolean linked,
        Double contributionPercent
) {
    public static GroupMemberResponse fromEntity(GroupMember m) {
        return new GroupMemberResponse(
                m.getId(),
                m.getStudentName(),
                m.getStudentId(),
                m.getEmail(),
                m.getUser() != null,
                m.getContributionPercent()
        );
    }
}
