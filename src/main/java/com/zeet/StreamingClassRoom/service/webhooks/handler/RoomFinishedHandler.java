package com.zeet.StreamingClassRoom.service.webhooks.handler;

import org.springframework.stereotype.Service;

import com.zeet.StreamingClassRoom.service.LiveSessionService;

import livekit.LivekitWebhook.WebhookEvent;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class RoomFinishedHandler implements LiveKitEventHandler {
    
    private final LiveSessionService liveSessionService;
    public String getEventType() {
        return "room_finished";
    }

    public void handleEvent(WebhookEvent event) {
        String livekitRoomName = event.getRoom().getName();

        System.out.println("[LiveKit] room_finished roomName = " + livekitRoomName);
    liveSessionService.endSessionByLivekitRoomName(livekitRoomName);
    }
}
