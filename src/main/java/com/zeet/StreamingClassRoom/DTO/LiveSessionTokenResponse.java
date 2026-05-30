package com.zeet.StreamingClassRoom.DTO;

import com.zeet.StreamingClassRoom.model.LiveSessionStatus;

public record LiveSessionTokenResponse (
        String sessionId,
        String classroomId,
        String roomName,
        String livekitUrl,
        String token,
        String identity,
        String username,
        LiveSessionStatus status) {
    
}
