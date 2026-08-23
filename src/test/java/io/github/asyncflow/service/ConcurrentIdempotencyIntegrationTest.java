package io.github.asyncflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.asyncflow.api.CreateTaskRequest;
import io.github.asyncflow.api.TaskResponse;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Tag("reliability")
@ActiveProfiles("test")
@SpringBootTest
class ConcurrentIdempotencyIntegrationTest {
    @Autowired TaskSubmissionService service;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;
    @Autowired ObjectMapper mapper;

    @MockitoBean RabbitTemplate rabbit;

    @Test
    void oneHundredConcurrentSubmissionsCreateOneTask() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            events.deleteAll();
            outbox.deleteAll();
            tasks.deleteAll();
            String requestId = "concurrent-" + UUID.randomUUID();
            CreateTaskRequest request = new CreateTaskRequest("SIMULATION",
                    mapper.readTree("{\"runId\":\"concurrency\"}"), 3, 0);
            ExecutorService pool = Executors.newFixedThreadPool(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<TaskResponse>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < 100; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return service.submit(requestId, request);
                    }));
                }
                start.countDown();
                Set<String> ids = new HashSet<>();
                int duplicateResponses = 0;
                for (Future<TaskResponse> future : futures) {
                    TaskResponse response = future.get();
                    ids.add(response.taskId());
                    if (response.deduplicated()) duplicateResponses++;
                }
                assertThat(ids).hasSize(1);
                assertThat(duplicateResponses).isEqualTo(99);
                assertThat(tasks.count()).isEqualTo(1);
                assertThat(outbox.count()).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        });
    }
}
