package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskEvent;
import io.github.asyncflow.domain.TaskStatus;

import java.time.Instant;

public record TaskEventResponse(long id, TaskStatus fromStatus, TaskStatus toStatus,
                                String source, String reason, Instant occurredAt) {
    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(event.getId(), event.getFromStatus(), event.getToStatus(),
                event.getSource(), event.getReason(), event.getOccurredAt());
    }
}
