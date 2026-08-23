package io.github.asyncflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "async_task")
public class TaskRecord {
    @Id
    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "idempotency_key", length = 128, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "task_type", length = 64, nullable = false)
    private String taskType;

    @Column(name = "payload", columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "simulate_failures", nullable = false)
    private int simulateFailures;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TaskRecord() {
    }

    public static TaskRecord create(String idempotencyKey, String taskType, String payload,
                                    int maxAttempts, int simulateFailures) {
        TaskRecord task = new TaskRecord();
        task.taskId = UUID.randomUUID().toString();
        task.idempotencyKey = idempotencyKey;
        task.taskType = taskType;
        task.payload = payload;
        task.status = TaskStatus.CREATED;
        task.maxAttempts = maxAttempts;
        task.simulateFailures = simulateFailures;
        task.createdAt = Instant.now();
        task.updatedAt = task.createdAt;
        return task;
    }

    public void transitionTo(TaskStatus next) {
        TaskStateMachine.validate(status, next);
        status = next;
        updatedAt = Instant.now();
    }

    public int beginAttempt() {
        if (status != TaskStatus.QUEUED && status != TaskStatus.RETRYING) {
            throw new IllegalStateException("Task cannot be processed from status " + status);
        }
        transitionTo(TaskStatus.RUNNING);
        attemptCount++;
        return attemptCount;
    }

    public void markRetry(String reason) {
        failureReason = truncate(reason);
        transitionTo(TaskStatus.RETRYING);
    }

    public void markDeadLettered(String reason) {
        failureReason = truncate(reason);
        transitionTo(TaskStatus.DEAD);
    }

    public void markSucceeded() {
        failureReason = null;
        transitionTo(TaskStatus.SUCCEEDED);
    }

    public void queue() {
        transitionTo(TaskStatus.QUEUED);
    }

    public void startCompensation() {
        transitionTo(TaskStatus.COMPENSATING);
    }

    public void cancel() {
        transitionTo(TaskStatus.CANCELLED);
    }

    public void markCompensated() {
        failureReason = null;
        transitionTo(TaskStatus.COMPENSATED);
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    public boolean shouldSimulateFailure() { return attemptCount <= simulateFailures; }
    public boolean canRetry() { return attemptCount < maxAttempts; }
    public String getTaskId() { return taskId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTaskType() { return taskType; }
    public String getPayload() { return payload; }
    public TaskStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public int getSimulateFailures() { return simulateFailures; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
