package com.zeet.StreamingClassRoom.DTO;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record CreateLiveSessionRequest(
        @NotBlank String title,
        String description,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        Integer emptyTimeoutSeconds,
        Integer departureTimeoutSeconds,
        Integer maxParticipants) {
}
