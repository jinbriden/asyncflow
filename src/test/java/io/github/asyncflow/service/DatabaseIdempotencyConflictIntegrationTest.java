package io.github.asyncflow.service;

import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.idempotency.IdempotencyStore;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("reliability")
@AsyncFlowSupport
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseIdempotencyConflictIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("asyncflow")
            .withUsername("asyncflow")
            .withPassword("asyncflow");

    @LocalServerPort int port;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;

    @MockitoBean IdempotencyStore idempotency;
    @MockitoBean RabbitTemplate rabbit;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @RepeatedTest(3)
    void databaseUniqueConstraintReturnsOneWinnerWhenReservationsRace() throws Exception {
        String key = TaskFixtures.key("db-conflict-");
        CyclicBarrier bothReserved = new CyclicBarrier(2);
        when(idempotency.getTaskId(key)).thenReturn(Optional.empty());
        when(idempotency.reserve(eq(key), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            bothReserved.await(10, TimeUnit.SECONDS);
            return true;
        });

        AsyncFlowApiClient api = new AsyncFlowApiClient(port);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Response> first = pool.submit(() -> api.submit(key, TaskFixtures.simulationBody()));
            Future<Response> second = pool.submit(() -> api.submit(key, TaskFixtures.simulationBody()));
            List<Response> responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertThat(responses).extracting(Response::statusCode)
                    .containsExactlyInAnyOrder(202, 200);
            Set<String> taskIds = responses.stream()
                    .map(response -> response.<String>path("taskId"))
                    .collect(Collectors.toSet());
            assertThat(taskIds).hasSize(1);
            assertThat(responses).extracting(response -> (Boolean) response.path("deduplicated"))
                    .containsExactlyInAnyOrder(false, true);
            assertThat(tasks.count()).isEqualTo(1);
            assertThat(events.count()).isEqualTo(1);
            assertThat(outbox.count()).isEqualTo(1);
            verify(idempotency).release(eq(key), anyString());
        } finally {
            pool.shutdownNow();
        }
    }
}
