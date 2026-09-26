package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskEvent;
import io.github.asyncflow.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record TaskEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) TaskStatus fromStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TaskStatus toStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt) {
    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(event.getId(), event.getFromStatus(), event.getToStatus(),
                event.getSource(), event.getReason(), event.getOccurredAt());
    }
}
