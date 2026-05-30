package com.zeet.StreamingClassRoom.model;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "livekit_webhook_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LiveKitWebhookEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "livekit_room_name")
    private String livekitRoomName;

    @Column(name = "livekit_room_sid")
    private String livekitRoomSid;

    @Column(name = "participant_identity")
    private String participantIdentity;

    @Column(name = "participant_sid")
    private String participantSid;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
