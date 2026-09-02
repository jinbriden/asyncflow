package io.github.asyncflow.framework.assertion;

import io.github.asyncflow.framework.client.StoreClient;

import static org.assertj.core.api.Assertions.assertThat;

public final class StoreAssertions {
    private StoreAssertions() {
    }

    public static void assertTaskCount(StoreClient store, long expected) {
        assertThat(store.taskCount()).isEqualTo(expected);
    }

    public static void assertNoTasks(StoreClient store) {
        assertTaskCount(store, 0);
    }

    public static void assertSingleTaskAndOutbox(StoreClient store) {
        assertThat(store.taskCount()).isEqualTo(1);
        assertThat(store.outboxCount()).isEqualTo(1);
    }

    public static void assertReportResultCount(StoreClient store, long expected) {
        assertThat(store.reportResultCount()).isEqualTo(expected);
    }

    public static void assertAttemptCount(StoreClient store, String taskId, int expected) {
        assertThat(store.requireTask(taskId).getAttemptCount()).isEqualTo(expected);
    }
}
