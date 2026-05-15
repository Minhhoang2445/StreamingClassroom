package com.zeet.StreamingClassRoom.service.webhooks;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zeet.StreamingClassRoom.service.webhooks.handler.LiveKitEventHandler;

import livekit.LivekitWebhook.WebhookEvent;

@Service
public class WebhooksDispatcher {
    

    private final Map<String, LiveKitEventHandler> handlersMap;
    public WebhooksDispatcher(List<LiveKitEventHandler> handlers) {
        this.handlersMap = handlers.stream()
            .collect(Collectors.toMap(LiveKitEventHandler::getEventType, handler -> handler));
        
    }
    public void dispatch(WebhookEvent event) {
        String eventType = event.getEvent();
        LiveKitEventHandler handler = handlersMap.get(eventType);
        if (handler != null) {
            handler.handleEvent(event);
        } else {
            System.out.println("No handler found for event type: " + eventType);
        }

    }
}
