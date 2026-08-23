package io.github.asyncflow.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

    @Override
    public Optional<String> getTaskId(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public boolean reserve(String key, String taskId, Duration ttl) {
        return values.putIfAbsent(key, taskId) == null;
    }

    @Override
    public void release(String key, String taskId) {
        values.remove(key, taskId);
    }
}
