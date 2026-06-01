package com.zeet.StreamingClassRoom.service;

import java.security.Principal;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeet.StreamingClassRoom.DTO.ChatMessageResponse;
import com.zeet.StreamingClassRoom.DTO.SendChatMessageRequest;
import com.zeet.StreamingClassRoom.exception.BadRequestException;
import com.zeet.StreamingClassRoom.exception.ForbiddenException;
import com.zeet.StreamingClassRoom.exception.ResourceNotFoundException;
import com.zeet.StreamingClassRoom.model.ChatMessage;
import com.zeet.StreamingClassRoom.model.ChatMessageStatus;
import com.zeet.StreamingClassRoom.model.LiveSession;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.ChatMessageRepository;
import com.zeet.StreamingClassRoom.repository.LiveSessionRepository;
import com.zeet.StreamingClassRoom.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatMessageRepository chatMessageRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final UserRepository userRepository;

    public ChatMessageResponse sendMessage(
            String sessionId,
            SendChatMessageRequest request,
            Principal principal
    ) {
        User currentUser = getCurrentUser(principal);
        LiveSession session = getSessionOrThrow(sessionId);

        validateContent(request.content());

        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setSender(currentUser);
        message.setContent(request.content().trim());
        message.setStatus(ChatMessageStatus.SENT);

        ChatMessage savedMessage = chatMessageRepository.save(message);

        return mapToResponse(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesBySession(
            String sessionId,
            Authentication authentication
    ) {
        getCurrentUser(authentication);
        getSessionOrThrow(sessionId);

        return chatMessageRepository
                .findBySessionIdAndStatusOrderByCreatedAtAsc(
                        sessionId,
                        ChatMessageStatus.SENT
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ForbiddenException("Authentication is required");
        }

        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private LiveSession getSessionOrThrow(String sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found"));
    }

    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("Message content is required");
        }

        if (content.trim().length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("Message content must not exceed 2000 characters");
        }
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
                message.getCreatedAt()
        );
    }
}