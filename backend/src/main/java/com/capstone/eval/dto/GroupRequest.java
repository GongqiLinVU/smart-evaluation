package com.capstone.eval.dto;

import java.util.List;

public record GroupRequest(
        String groupCode,
        String groupName,
        List<GroupMemberRequest> members
) {}
