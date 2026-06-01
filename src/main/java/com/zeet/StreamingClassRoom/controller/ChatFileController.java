package com.zeet.StreamingClassRoom.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.zeet.StreamingClassRoom.DTO.ChatMessageResponse;
import com.zeet.StreamingClassRoom.service.ChatFileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatFileController {

    private final ChatFileService chatFileService;

    @PostMapping("/api/sessions/{sessionId}/messages/files")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<ChatMessageResponse> uploadChatFile(
            @PathVariable String sessionId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "caption", required = false) String caption,
            Principal principal
    ) {
        ChatMessageResponse response =
                chatFileService.uploadChatFile(sessionId, file, caption, principal);

        return ResponseEntity.ok(response);
    }
}