package com.zeet.StreamingClassRoom.DTO;

import java.time.LocalDateTime;

import com.zeet.StreamingClassRoom.model.LiveSessionStatus;

public record LiveSessionResponse(
        String id,
        String classroomId,
        String classroomName,
        String hostId,
        String hostUsername,
        String title,
        String description,
        LiveSessionStatus status,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        LocalDateTime actualStartTime,
        LocalDateTime actualEndTime,
        String livekitRoomName,
        String livekitRoomSid,
        Integer emptyTimeoutSeconds,
        Integer departureTimeoutSeconds,
        Integer maxParticipants,
        String roomMetadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
