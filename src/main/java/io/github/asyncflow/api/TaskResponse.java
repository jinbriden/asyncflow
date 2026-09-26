package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record TaskResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String taskId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String taskType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TaskStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxAttempts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String failureReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean deduplicated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ReportResultResponse result,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static TaskResponse from(TaskRecord task, boolean deduplicated) {
        return from(task, deduplicated, null);
    }

    public static TaskResponse from(TaskRecord task,
                                    boolean deduplicated,
                                    ReportResultResponse result) {
        return new TaskResponse(task.getTaskId(), task.getTaskType(),
                task.getStatus(), task.getAttemptCount(),
                task.getMaxAttempts(), task.getFailureReason(),
                deduplicated,
                result,
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
