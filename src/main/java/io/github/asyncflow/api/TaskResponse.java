package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;

import java.time.Instant;

public record TaskResponse(
        String taskId,
        String taskType,
        TaskStatus status,
        int attemptCount,
        int maxAttempts,
        String failureReason,
        boolean deduplicated,
        ReportResultResponse result,
        Instant createdAt,
        Instant updatedAt
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
