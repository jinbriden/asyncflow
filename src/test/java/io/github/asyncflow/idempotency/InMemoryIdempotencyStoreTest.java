package io.github.asyncflow.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    @Test
    void releaseOnlyRemovesReservationOwnedBySameTask() {
        assertThat(store.reserve("request-1", "task-a", Duration.ofMinutes(1))).isTrue();

        store.release("request-1", "task-b");
        assertThat(store.getTaskId("request-1")).contains("task-a");

        store.release("request-1", "task-a");
        assertThat(store.getTaskId("request-1")).isEmpty();
    }
}
