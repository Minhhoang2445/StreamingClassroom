package com.zeet.StreamingClassRoom.DTO;

import java.time.LocalDateTime;

import com.zeet.StreamingClassRoom.model.LiveSessionStatus;

public record UpdateLiveSessionRequest(
        String title,
        String description,
        LiveSessionStatus status,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        Integer emptyTimeoutSeconds,
        Integer departureTimeoutSeconds,
        Integer maxParticipants) {
}
