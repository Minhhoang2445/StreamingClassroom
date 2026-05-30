package com.zeet.StreamingClassRoom.DTO;

public record UpdateClassroomRequest(
        String name,
        String description,
        String teacherId,
        String classCode) {
}
