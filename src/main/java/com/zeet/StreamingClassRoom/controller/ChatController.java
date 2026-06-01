package com.zeet.StreamingClassRoom.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.zeet.StreamingClassRoom.DTO.ChatMessageResponse;
import com.zeet.StreamingClassRoom.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/api/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<List<ChatMessageResponse>> getMessagesBySession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        List<ChatMessageResponse> messages =
                chatService.getMessagesBySession(sessionId, authentication);

        return ResponseEntity.ok(messages);
    }
}