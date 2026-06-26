package com.capstone.eval.dto;

import com.capstone.eval.model.Group;

import java.time.LocalDateTime;
import java.util.List;

public record GroupResponse(
        Long id,
        String groupCode,
        String groupName,
        Long projectId,
        List<GroupMemberResponse> members,
        LocalDateTime createdAt
) {
    public static GroupResponse fromEntity(Group g) {
        List<GroupMemberResponse> memberResponses = g.getMembers() == null
                ? List.of()
                : g.getMembers().stream().map(GroupMemberResponse::fromEntity).toList();
        return new GroupResponse(
                g.getId(),
                g.getGroupCode(),
                g.getGroupName(),
                g.getProject().getId(),
                memberResponses,
                g.getCreatedAt()
        );
    }
}
