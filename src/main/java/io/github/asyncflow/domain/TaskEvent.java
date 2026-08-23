package io.github.asyncflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_event")
public class TaskEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32, nullable = false)
    private TaskStatus toStatus;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TaskEvent() {
    }

    public static TaskEvent of(String taskId, TaskStatus from, TaskStatus to, String source, String reason) {
        TaskEvent event = new TaskEvent();
        event.taskId = taskId;
        event.fromStatus = from;
        event.toStatus = to;
        event.source = source;
        event.reason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
        event.occurredAt = Instant.now();
        return event;
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public TaskStatus getFromStatus() { return fromStatus; }
    public TaskStatus getToStatus() { return toStatus; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
