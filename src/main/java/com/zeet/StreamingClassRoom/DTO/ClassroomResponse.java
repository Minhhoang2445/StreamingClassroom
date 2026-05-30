package com.zeet.StreamingClassRoom.DTO;

import java.time.LocalDateTime;

public record ClassroomResponse(
        String id,
        String name,
        String description,
        String classCode,
        String teacherId,
        String teacherUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long memberCount) {
}
