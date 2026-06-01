package com.zeet.StreamingClassRoom.service;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.zeet.StreamingClassRoom.DTO.ChatMessageResponse;
import com.zeet.StreamingClassRoom.exception.ForbiddenException;
import com.zeet.StreamingClassRoom.exception.ResourceNotFoundException;
import com.zeet.StreamingClassRoom.model.ChatMessage;
import com.zeet.StreamingClassRoom.model.ChatMessageStatus;
import com.zeet.StreamingClassRoom.model.ChatMessageType;
import com.zeet.StreamingClassRoom.model.LiveSession;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.ChatMessageRepository;
import com.zeet.StreamingClassRoom.repository.LiveSessionRepository;
import com.zeet.StreamingClassRoom.repository.UserRepository;
import com.zeet.StreamingClassRoom.service.FileStorageService.StoredFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatFileService {

    private final ChatMessageRepository chatMessageRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public ChatMessageResponse uploadChatFile(
            String sessionId,
            MultipartFile file,
            String caption,
            Principal principal
    ) {
        User currentUser = getCurrentUser(principal);

        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found"));

        StoredFile storedFile = fileStorageService.storeChatFile(sessionId, file);

        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setSender(currentUser);
        message.setContent(caption == null ? "" : caption.trim());
        message.setStatus(ChatMessageStatus.SENT);
        message.setMessageType(ChatMessageType.FILE);
        message.setFileName(storedFile.originalFileName());
        message.setFilePath(storedFile.filePath());
        message.setFileType(storedFile.fileType());
        message.setFileSize(storedFile.fileSize());

        ChatMessage savedMessage = chatMessageRepository.save(message);

        String fileUrl = publicBaseUrl + "/api/files/chat/" + savedMessage.getId();
        savedMessage.setFileUrl(fileUrl);

        ChatMessage savedWithUrl = chatMessageRepository.save(savedMessage);

        ChatMessageResponse response = mapToResponse(savedWithUrl);

        messagingTemplate.convertAndSend(
                "/topic/sessions/" + sessionId + "/chat",
                response
        );

        return response;
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ForbiddenException("Authentication is required");
        }

        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private ChatMessageResponse mapToResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getSender().getRole(),
                message.getContent(),
                message.getStatus(),
                message.getMessageType(),
                message.getFileName(),
                message.getFileUrl(),
                message.getFileType(),
                message.getFileSize(),
                message.getCreatedAt()
        );
    }
}