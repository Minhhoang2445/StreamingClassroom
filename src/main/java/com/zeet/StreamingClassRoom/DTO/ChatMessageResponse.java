package com.zeet.StreamingClassRoom.DTO;

import java.time.LocalDateTime;

import com.zeet.StreamingClassRoom.model.ChatMessageStatus;
import com.zeet.StreamingClassRoom.model.Role;

public record ChatMessageResponse(
        String id,
        String sessionId,
        String senderId,
        String senderUsername,
        Role senderRole,
        String content,
        ChatMessageStatus status,
        LocalDateTime createdAt
) {
}