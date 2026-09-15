package io.github.asyncflow.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {
    private static final String KEY = "request-1";
    private static final String REDIS_KEY = "asyncflow:idempotency:" + KEY;

    @Mock StringRedisTemplate redis;

    private RedisIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redis);
    }

    @Test
    void releaseUsesOneAtomicCompareAndDeleteCommand() {
        store.release(KEY, "task-a");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of(REDIS_KEY)), eq("task-a"));
        assertThat(script.getValue().getResultType()).isEqualTo(Long.class);
        assertThat(script.getValue().getScriptAsString())
                .contains("redis.call('get', KEYS[1])", "redis.call('del', KEYS[1])");
    }

    @Test
    void releaseDoesNotMaskAtomicCleanupFailure() {
        when(redis.<Long>execute(any(), eq(List.of(REDIS_KEY)), eq("task-a")))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatCode(() -> store.release(KEY, "task-a")).doesNotThrowAnyException();
    }
}
