package com.zeet.StreamingClassRoom.service.webhooks.handler;

import livekit.LivekitWebhook.WebhookEvent;

public interface LiveKitEventHandler {
    String getEventType();
    void handleEvent(WebhookEvent event);
}
