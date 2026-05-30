package com.zeet.StreamingClassRoom.DTO;

import jakarta.validation.constraints.NotBlank;

public record JoinClassroomRequest(@NotBlank String classCode) {
}
