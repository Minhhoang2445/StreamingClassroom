package com.zeet.StreamingClassRoom.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.zeet.StreamingClassRoom.DTO.ChatMessageResponse;
import com.zeet.StreamingClassRoom.DTO.SendChatMessageRequest;
import com.zeet.StreamingClassRoom.service.ChatService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/sessions/{sessionId}/chat.send")
    public void sendMessage(
            @DestinationVariable String sessionId,
            SendChatMessageRequest request,
            Principal principal
    ) {
        try {
            System.out.println("=== CHAT MESSAGE RECEIVED ===");
            System.out.println("sessionId = " + sessionId);
            System.out.println("content = " + (request != null ? request.content() : "null"));
            System.out.println("principal = " + (principal != null ? principal.getName() : "null"));

            ChatMessageResponse response = chatService.sendMessage(sessionId, request, principal);

            String destination = "/topic/sessions/" + sessionId + "/chat";

            messagingTemplate.convertAndSend(destination, response);

            System.out.println("CHAT BROADCAST SUCCESS");
            System.out.println("destination = " + destination);
            System.out.println("messageId = " + response.id());
        } catch (Exception e) {
            System.out.println("CHAT ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}