package com.zeet.StreamingClassRoom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zeet.StreamingClassRoom.model.ChatMessage;
import com.zeet.StreamingClassRoom.model.ChatMessageStatus;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findBySessionIdAndStatusOrderByCreatedAtAsc(
            String sessionId,
            ChatMessageStatus status
    );
}