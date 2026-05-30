package com.zeet.StreamingClassRoom.DTO;

import jakarta.validation.constraints.NotBlank;

public record CreateClassroomRequest(
        @NotBlank String name,
        String description,
        @NotBlank String teacherId,
        @NotBlank String classCode) {
}
