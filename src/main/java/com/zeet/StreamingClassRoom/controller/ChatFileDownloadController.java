package com.zeet.StreamingClassRoom.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.zeet.StreamingClassRoom.exception.ResourceNotFoundException;
import com.zeet.StreamingClassRoom.model.ChatMessage;
import com.zeet.StreamingClassRoom.model.ChatMessageType;
import com.zeet.StreamingClassRoom.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatFileDownloadController {

    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/api/files/chat/{messageId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<Resource> downloadChatFile(
            @PathVariable String messageId
    ) throws IOException {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (message.getMessageType() != ChatMessageType.FILE) {
            throw new ResourceNotFoundException("File message not found");
        }

        if (message.getFilePath() == null || message.getFilePath().isBlank()) {
            throw new ResourceNotFoundException("File path not found");
        }

        Path filePath = Path.of(message.getFilePath()).normalize();

        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("File not found on server");
        }

        Resource resource = new UrlResource(filePath.toUri());

        String contentType = message.getFileType() != null
                ? message.getFileType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        ContentDisposition contentDisposition = ContentDisposition
                .attachment()
                .filename(message.getFileName())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(message.getFileSize())
                .body(resource);
    }
}