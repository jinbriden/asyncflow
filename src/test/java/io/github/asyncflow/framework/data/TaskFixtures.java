package io.github.asyncflow.framework.data;

import io.github.asyncflow.domain.TaskRecord;

import java.util.UUID;

public final class TaskFixtures {
    public static final String DEAD_PAYLOAD = "{\"runId\":\"dead\"}";
    public static final String WIREMOCK_PAYLOAD = "{\"runId\":\"wiremock\"}";
    public static final String PERMANENT_FAILURE_PAYLOAD = "{\"forcePermanentFailure\":true}";
    public static final String TRACE_ID = "trace-integration-001";

    private TaskFixtures() {
    }

    public static String key(String prefix) {
        return prefix + UUID.randomUUID();
    }

    public static String simulationBody() {
        return "{\"type\":\"SIMULATION\",\"payload\":{\"runId\":\"concurrency\"},\"maxAttempts\":3,\"simulateFailures\":0}";
    }

    public static TaskRecord deadTask(String idempotencyKey) {
        TaskRecord task = TaskRecord.create(idempotencyKey, "REPORT", DEAD_PAYLOAD, 1, 1);
        task.queue();
        task.beginAttempt();
        task.markDeadLettered("injected");
        return task;
    }

    public static TaskRecord queued(String idempotencyKey, String type, String payload,
                                    int maxAttempts, int failures) {
        TaskRecord task = TaskRecord.create(idempotencyKey, type, payload, maxAttempts, failures);
        task.queue();
        return task;
    }
}
