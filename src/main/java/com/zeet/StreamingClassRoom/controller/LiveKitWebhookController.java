package com.zeet.StreamingClassRoom.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.protobuf.util.JsonFormat;
import com.zeet.StreamingClassRoom.service.webhooks.WebhooksDispatcher;

import livekit.LivekitWebhook.WebhookEvent;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks")
public class LiveKitWebhookController {
    
    private final WebhooksDispatcher webhooksDispatcher;

        @PostMapping(
        value = "/livekit",
        consumes = {
            "application/json",
            "application/webhook+json"
        }
    )
    public ResponseEntity<Void> handleLiveKitWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws Exception {
        System.out.println("[LiveKit webhook] Authorization = " + authorization);
        System.out.println("[LiveKit webhook] Raw body = " + rawBody);

        // TODO: verify authorization header later

        WebhookEvent.Builder builder = WebhookEvent.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(rawBody, builder);

        WebhookEvent event = builder.build();
        webhooksDispatcher.dispatch(event);

        return ResponseEntity.ok().build();
    }
        
}
