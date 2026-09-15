package io.github.asyncflow.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@Profile("!test")
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final String PREFIX = "asyncflow:idempotency:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private final StringRedisTemplate redis;

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> getTaskId(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + key));
        } catch (RuntimeException ex) {
            log.warn("Redis idempotency lookup unavailable; falling back to database unique key: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean reserve(String key, String taskId, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + key, taskId, ttl));
        } catch (RuntimeException ex) {
            log.warn("Redis idempotency reservation unavailable; database unique key remains authoritative: {}", ex.getMessage());
            return true;
        }
    }

    @Override
    public void release(String key, String taskId) {
        try {
            redis.execute(RELEASE_SCRIPT, List.of(PREFIX + key), taskId);
        } catch (RuntimeException ex) {
            log.debug("Redis reservation cleanup skipped while unavailable: {}", ex.getMessage());
        }
    }
}
