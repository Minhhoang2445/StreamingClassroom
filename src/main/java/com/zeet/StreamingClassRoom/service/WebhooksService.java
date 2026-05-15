package com.zeet.StreamingClassRoom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.zeet.StreamingClassRoom.service.webhooks.WebhooksDispatcher;

import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class WebhooksService {
    
    @Value("${livekit.api.key}")
    private String LIVEKIT_API_KEY; 

    @Value("${livekit.api.secret}")
    private String LIVEKIT_API_SECRET; 

    private final WebhooksDispatcher webhooksDispatcher;
    public void handleIncomingEvent(String payload, String authHeader) {
        try {
            WebhookReceiver receiver = new WebhookReceiver(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
            WebhookEvent event = receiver.receive(payload, authHeader);
            webhooksDispatcher.dispatch(event);
        }
        catch (Exception e) {
            System.err.println("Failed to process webhook: " + e.getMessage());
        }
    }
}
