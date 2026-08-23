package io.github.asyncflow.idempotency;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {
    Optional<String> getTaskId(String key);
    boolean reserve(String key, String taskId, Duration ttl);
    void release(String key, String taskId);
}
