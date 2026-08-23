package io.github.asyncflow.reliability;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.asyncflow.api.CreateTaskRequest;
import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.OutboxPublisher;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.service.TaskProcessor;
import io.github.asyncflow.service.TaskSubmissionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Tag("reliability")
@ActiveProfiles("container")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "asyncflow.scanner.enabled=false",
        "asyncflow.outbox.scheduler-enabled=false",
        "asyncflow.downstream.timeout=PT0.2S"
})
class InfrastructureReliabilityTest {
    private static final Network NETWORK = Network.newNetwork();
    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("asyncflow").withUsername("asyncflow").withPassword("asyncflow")
            .withNetwork(NETWORK).withNetworkAliases("mysql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379).withNetwork(NETWORK).withNetworkAliases("redis");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine")
            .withNetwork(NETWORK).withNetworkAliases("rabbitmq");

    @Container
    static final ToxiproxyContainer TOXI = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0")
            .withNetwork(NETWORK);

    private static ToxiproxyContainer.ContainerProxy mysqlProxy;
    private static ToxiproxyContainer.ContainerProxy redisProxy;
    private static ToxiproxyContainer.ContainerProxy rabbitProxy;

    @Autowired TaskSubmissionService submissions;
    @Autowired TaskProcessor processor;
    @Autowired TaskRepository tasks;
    @Autowired OutboxEventRepository outbox;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + TOXI.getHost() + ":"
                + mysqlProxy().getProxyPort() + "/asyncflow?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                +"&connectTimeout=2000"
                + "&socketTimeout=2000"
                + "&tcpKeepAlive=false"
        );
        registry.add("spring.datasource.username", () -> "asyncflow");
        registry.add("spring.datasource.password", () -> "asyncflow");
        // 获取连接最多等待3秒
        registry.add("spring.datasource.hikari.connection-timeout", () -> 3000);
        // 连接有效性检测最多等待1秒
        registry.add("spring.datasource.hikari.validation-timeout", () -> 1000);
        // 故障测试不需要大量数据库连接
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.data.redis.host", TOXI::getHost);
        registry.add("spring.data.redis.port", () -> redisProxy().getProxyPort());
        registry.add("spring.rabbitmq.host", TOXI::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitProxy().getProxyPort());
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("asyncflow.downstream.base-url", () -> {
            if (!WIREMOCK.isRunning()) WIREMOCK.start();
            return WIREMOCK.baseUrl();
        });
    }

    @AfterEach
    void restoreNetwork() {
        mysqlProxy().setConnectionCut(false);
        redisProxy().setConnectionCut(false);
        rabbitProxy().setConnectionCut(false);
        WIREMOCK.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        if (WIREMOCK.isRunning()) WIREMOCK.stop();
        NETWORK.close();
    }

    @Test
    void isolatedDependenciesAreHealthy() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(RABBIT.isRunning()).isTrue();
        assertThat(tasks.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void realRedisAndDatabaseDeduplicateRepeatedRequest() throws Exception {
        String key = unique("redis-idempotency");
        CreateTaskRequest request = request("REPORT", 3, 0);
        String first = submissions.submit(key, request).taskId();
        String second = submissions.submit(key, request).taskId();
        assertThat(second).isEqualTo(first);
        assertThat(tasks.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void redisOutageFallsBackToDatabaseAuthority() throws Exception {
        redisProxy().setConnectionCut(true);
        String key = unique("redis-down");
        String taskId = submissions.submit(key, request("REPORT", 3, 0)).taskId();
        assertThat(tasks.findById(taskId)).isPresent();
    }

    @Test
    @Timeout(
            value = 20,
            unit = TimeUnit.SECONDS,
            threadMode = Timeout.ThreadMode.SEPARATE_THREAD
    )
    void rabbitOutageLeavesOutboxPendingThenRecovers() throws Exception {
        String taskId = submissions.submit(
                unique("rabbit-down"),
                request("REPORT", 3, 0)
        ).taskId();

        rabbitProxy().setConnectionCut(true);

        try {
            // RabbitMQ断开时尝试发布，事件应该继续留在Outbox
            outboxPublisher.publishPending();

            OutboxEvent pending = outbox.findAll().stream()
                    .filter(event -> event.getAggregateId().equals(taskId))
                    .findFirst()
                    .orElseThrow();

            assertThat(pending.getPublishedAt()).isNull();
            assertThat(pending.getPublishAttempts()).isGreaterThan(0);
        } finally {
            rabbitProxy().setConnectionCut(false);
        }

        await()
                .pollInterval(Duration.ofMillis(250))
                .atMost(Duration.ofSeconds(8))
                .untilAsserted(() -> {
                    // TCP连接恢复需要时间；每轮重新尝试所有尚未确认的Outbox事件。
                    outboxPublisher.publishPending();
                    OutboxEvent published = outbox.findAll().stream()
                            .filter(event -> event.getAggregateId().equals(taskId))
                            .findFirst()
                            .orElseThrow();

                    assertThat(published.getPublishedAt()).isNotNull();
                    assertThat(tasks.findById(taskId).orElseThrow().getStatus())
                            .isEqualTo(TaskStatus.QUEUED);
                });
    }

    @Test
    @Timeout(
            value = 15,
            unit = TimeUnit.SECONDS,
            threadMode = Timeout.ThreadMode.SEPARATE_THREAD
    )
    void mysqlNetworkCutIsVisibleAndConnectionRecovers() {
        mysqlProxy().setConnectionCut(true);

        try {
            assertThatThrownBy(tasks::count)
                    .isInstanceOf(RuntimeException.class);
        } finally {
            // 即使断言失败或者超时，也必须恢复网络
            mysqlProxy().setConnectionCut(false);
        }

        await()
                .pollInterval(Duration.ofMillis(250))
                .atMost(Duration.ofSeconds(8))
                .untilAsserted(() ->
                        assertThat(tasks.count()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void downstreamTimesOutTwiceThenThirdAttemptSucceeds() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/execute")).inScenario("flaky")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willSetStateTo("second").willReturn(aResponse().withFixedDelay(800).withStatus(200)));
        WIREMOCK.stubFor(post(urlEqualTo("/execute")).inScenario("flaky").whenScenarioStateIs("second")
                .willSetStateTo("healthy").willReturn(aResponse().withFixedDelay(800).withStatus(200)));
        WIREMOCK.stubFor(post(urlEqualTo("/execute")).inScenario("flaky").whenScenarioStateIs("healthy")
                .willReturn(aResponse().withStatus(204)));
        TaskRecord task = queuedTask("CALLBACK", 3, 0, "{\"runId\":\"wiremock\"}");
        processor.process(new TaskMessage(task.getTaskId()));
        processor.process(new TaskMessage(task.getTaskId()));
        processor.process(new TaskMessage(task.getTaskId()));
        TaskRecord completed = tasks.findById(task.getTaskId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(completed.getAttemptCount()).isEqualTo(3);
        WIREMOCK.verify(3, postRequestedFor(urlEqualTo("/execute")));
    }

    @Test
    void persistentBusinessFailureEndsInDeadState() {
        TaskRecord task = queuedTask("REPORT", 2, 0, "{\"forcePermanentFailure\":true}");
        processor.process(new TaskMessage(task.getTaskId()));
        processor.process(new TaskMessage(task.getTaskId()));
        TaskRecord failed = tasks.findById(task.getTaskId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(failed.getFailureReason()).contains("Permanent business failure");
    }

    private CreateTaskRequest request(String type, int maxAttempts, int failures) throws Exception {
        String payload = "REPORT".equals(type)
                ? "{\"reportName\":\"container-report\",\"requestedBy\":\"qa@example.com\",\"records\":[{\"orderId\":\"SO-C1\",\"region\":\"East\",\"product\":\"Keyboard\",\"quantity\":1,\"unitPrice\":199.50}]}"
                : "{\"runId\":\"container\"}";
        return new CreateTaskRequest(type, mapper.readTree(payload), maxAttempts, failures);
    }

    private TaskRecord queuedTask(String type, int maxAttempts, int failures, String payload) {
        TaskRecord task = TaskRecord.create(unique("worker"), type, payload, maxAttempts, failures);
        task.queue();
        return tasks.save(task);
    }

    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }

    private static synchronized ToxiproxyContainer.ContainerProxy mysqlProxy() {
        if (mysqlProxy == null) mysqlProxy = TOXI.getProxy(MYSQL, 3306);
        return mysqlProxy;
    }

    private static synchronized ToxiproxyContainer.ContainerProxy redisProxy() {
        if (redisProxy == null) redisProxy = TOXI.getProxy(REDIS, 6379);
        return redisProxy;
    }

    private static synchronized ToxiproxyContainer.ContainerProxy rabbitProxy() {
        if (rabbitProxy == null) rabbitProxy = TOXI.getProxy(RABBIT, 5672);
        return rabbitProxy;
    }
}
