package com.zeet.StreamingClassRoom.DTO;

import java.time.LocalDateTime;

import com.zeet.StreamingClassRoom.model.ClassroomMemberRole;
import com.zeet.StreamingClassRoom.model.ClassroomMemberStatus;

public record ClassroomMemberResponse(
        String id,
        String userId,
        String username,
        String classroomId,
        String classroomName,
        ClassroomMemberRole role,
        ClassroomMemberStatus status,
        LocalDateTime joinedAt) {
}
