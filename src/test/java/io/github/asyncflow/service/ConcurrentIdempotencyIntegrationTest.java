package io.github.asyncflow.service;

import io.github.asyncflow.framework.assertion.StoreAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.client.StoreClient;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.framework.scenario.ConcurrentSubmitScenario;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Tag("reliability")
@AsyncFlowSupport
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentIdempotencyIntegrationTest {
    @LocalServerPort int port;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;

    @MockitoBean RabbitTemplate rabbit;

    private AsyncFlowApiClient api;
    private StoreClient store;

    @BeforeEach
    void setUp() {
        api = new AsyncFlowApiClient(port);
        store = new StoreClient(tasks, events, outbox);
    }

    @Test
    void oneHundredConcurrentSubmissionsCreateOneTask() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            String requestId = TaskFixtures.key("concurrent-");
            ConcurrentSubmitScenario.Result result = new ConcurrentSubmitScenario()
                    .submitSameKey(api, requestId, TaskFixtures.simulationBody(), 100, 20);
            assertThat(result.taskIds()).hasSize(1);
            assertThat(result.duplicateResponses()).isEqualTo(99);
            StoreAssertions.assertSingleTaskAndOutbox(store);
        });
    }
}
