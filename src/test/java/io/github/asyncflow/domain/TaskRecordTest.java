package io.github.asyncflow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRecordTest {
    @Test
    void createsTaskWithSafeDefaults() {
        TaskRecord task = task(3, 0);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getTaskId()).hasSize(36);
        assertThat(task.getCreatedAt()).isNotNull();
    }

    @Test
    void beginsFirstAttemptFromQueued() {
        TaskRecord task = task(3, 0);
        task.queue();
        assertThat(task.beginAttempt()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void simulatedFailureBudgetUsesCurrentAttempt() {
        TaskRecord task = task(3, 2);
        task.queue();
        task.beginAttempt();
        assertThat(task.shouldSimulateFailure()).isTrue();
        task.markRetry("timeout");
        task.beginAttempt();
        assertThat(task.shouldSimulateFailure()).isTrue();
    }

    @Test
    void thirdAttemptSucceedsAfterTwoInjectedFailures() {
        TaskRecord task = task(3, 2);
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (attempt == 1) task.queue();
            task.beginAttempt();
            task.markRetry("timeout");
        }
        task.beginAttempt();
        assertThat(task.shouldSimulateFailure()).isFalse();
        task.markSucceeded();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    void retryBudgetStopsAtConfiguredLimit() {
        TaskRecord task = task(2, 2);
        task.queue();
        task.beginAttempt();
        assertThat(task.canRetry()).isTrue();
        task.markRetry("first");
        task.beginAttempt();
        assertThat(task.canRetry()).isFalse();
    }

    @Test
    void truncatesFailureEvidenceToDatabaseLimit() {
        TaskRecord task = task(1, 1);
        task.queue();
        task.beginAttempt();
        task.markDeadLettered("x".repeat(600));
        assertThat(task.getFailureReason()).hasSize(500);
    }

    @Test
    void clearsFailureWhenTaskEventuallySucceeds() {
        TaskRecord task = task(2, 1);
        task.queue();
        task.beginAttempt();
        task.markRetry("temporary");
        task.beginAttempt();
        task.markSucceeded();
        assertThat(task.getFailureReason()).isNull();
    }

    @Test
    void rejectsProcessingBeforeQueueing() {
        TaskRecord task = task(3, 0);
        assertThatThrownBy(task::beginAttempt).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supportsDeadLetterCompensation() {
        TaskRecord task = deadTask();
        task.startCompensation();
        task.markCompensated();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPENSATED);
    }

    @Test
    void cancellationIsTerminal() {
        TaskRecord task = task(3, 0);
        task.cancel();
        assertThatThrownBy(task::queue).isInstanceOf(IllegalStateException.class);
    }

    private TaskRecord deadTask() {
        TaskRecord task = task(1, 1);
        task.queue();
        task.beginAttempt();
        task.markDeadLettered("failed");
        return task;
    }

    private TaskRecord task(int maxAttempts, int failures) {
        return TaskRecord.create("key", "REPORT", "{\"runId\":\"test\"}", maxAttempts, failures);
    }
}
