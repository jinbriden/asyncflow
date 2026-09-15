package io.github.asyncflow.pipeline;

import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.framework.assertion.ApiAssertions;
import io.github.asyncflow.framework.assertion.ReportAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.client.StoreClient;
import io.github.asyncflow.framework.data.ReportTestDataFactory;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.report.ReportResultRepository;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItems;

@Tag("smoke")
@Tag("reliability")
@AsyncFlowSupport
@ActiveProfiles("container")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.task.scheduling.enabled=true",
        "asyncflow.outbox.enabled=true",
        "asyncflow.outbox.scheduler-enabled=true",
        "asyncflow.outbox.poll-interval=200",
        "asyncflow.scanner.enabled=false"
})
class AsyncPipelineIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("asyncflow").withUsername("asyncflow").withPassword("asyncflow");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @TempDir static Path reportDirectory;

    @LocalServerPort int port;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;
    @Autowired ReportResultRepository reports;

    private AsyncFlowApiClient api;
    private StoreClient store;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("asyncflow.report.storage-directory", reportDirectory::toString);
    }

    @BeforeEach
    void wireFramework() {
        api = new AsyncFlowApiClient(port);
        store = new StoreClient(tasks, events, outbox, reports);
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void submittedReportCompletesThroughOutboxBrokerAndListener() {
        String key = TaskFixtures.key("pipeline-smoke-");
        Response submitted = api.submit(key, ReportTestDataFactory.validReportBody());
        String taskId = ApiAssertions.taskId(submitted);
        ApiAssertions.assertAccepted(submitted);

        await()
                .pollInterval(Duration.ofMillis(250))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(store.requireTask(taskId).getStatus())
                        .isEqualTo(TaskStatus.SUCCEEDED));

        OutboxEvent published = publishedEvent(taskId);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(store.requireTask(taskId).getAttemptCount()).isEqualTo(1);

        api.events(taskId).then().statusCode(200)
                .body("source", hasItems("API", "OUTBOX", "WORKER"))
                .body("toStatus", hasItems("CREATED", "QUEUED", "RUNNING", "SUCCEEDED"));

        ReportAssertions.assertQueryableRegionalSales(api.getTask(taskId), taskId);
        ReportAssertions.assertDownloadableRegionalSalesCsv(api.downloadResult(taskId));
    }

    private OutboxEvent publishedEvent(String taskId) {
        List<OutboxEvent> matches = store.outboxEvents().stream()
                .filter(event -> event.getAggregateId().equals(taskId))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }
}
