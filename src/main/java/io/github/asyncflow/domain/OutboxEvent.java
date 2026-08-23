package io.github.asyncflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "aggregate_id", length = 36, nullable = false)
    private String aggregateId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    protected OutboxEvent() {
    }

    public static OutboxEvent taskCreated(String taskId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID().toString();
        event.aggregateId = taskId;
        event.eventType = "TASK_CREATED";
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }

    public void markPublished() {
        publishedAt = Instant.now();
        publishAttempts++;
    }

    public void markPublishFailed() {
        publishAttempts++;
    }

    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getPublishAttempts() { return publishAttempts; }
}
