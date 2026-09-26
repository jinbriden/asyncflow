package io.github.asyncflow.reliability;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.framework.assertion.ApiAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.data.ReportTestDataFactory;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.framework.scenario.WorkerScenario;
import io.github.asyncflow.messaging.OutboxPublisher;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.service.TaskProcessor;
import org.junit.jupiter.api.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
@AsyncFlowSupport
@ActiveProfiles("container")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
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

    @LocalServerPort int port;
    @Autowired TaskProcessor processor;
    @Autowired TaskRepository tasks;
    @Autowired OutboxEventRepository outbox;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired RabbitTemplate rabbit;

    private AsyncFlowApiClient api;
    private WorkerScenario worker;

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

    @BeforeEach
    void wireFramework() {
        api = new AsyncFlowApiClient(port);
        worker = new WorkerScenario(tasks, processor);
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
        String key = TaskFixtures.key("redis-idempotency-");
        String body = ReportTestDataFactory.containerReportBody();
        String first = ApiAssertions.taskId(api.submit(key, body));
        String second = ApiAssertions.taskId(api.submit(key, body));
        assertThat(second).isEqualTo(first);
        assertThat(tasks.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void redisOutageFallsBackToDatabaseAuthority() throws Exception {
        redisProxy().setConnectionCut(true);
        String key = TaskFixtures.key("redis-down-");
        String taskId = ApiAssertions.taskId(api.submit(key, ReportTestDataFactory.containerReportBody()));
        assertThat(tasks.findById(taskId)).isPresent();
    }

    @Test
    @Timeout(
            value = 20,
            unit = TimeUnit.SECONDS,
            threadMode = Timeout.ThreadMode.SEPARATE_THREAD
    )
    void rabbitOutageLeavesOutboxPendingThenRecovers() throws Exception {
        String taskId = ApiAssertions.taskId(api.submit(
                TaskFixtures.key("rabbit-down-"),
                ReportTestDataFactory.containerReportBody()));

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
            value = 60,
            unit = TimeUnit.SECONDS,
            threadMode = Timeout.ThreadMode.SEPARATE_THREAD
    )
    void persistentTaskMessageSurvivesBrokerRestart() throws Exception {
        purgeRabbitQueues();
        String taskId = ApiAssertions.taskId(api.submit(
                TaskFixtures.key("rabbit-restart-"),
                ReportTestDataFactory.containerReportBody()));

        outboxPublisher.publishPending();

        await()
                .pollInterval(Duration.ofMillis(250))
                .atMost(Duration.ofSeconds(8))
                .untilAsserted(() -> {
                    assertThat(publishedEvent(taskId).getPublishedAt()).isNotNull();
                    assertThat(taskQueueMessageCount()).isEqualTo(1);
                });

        RABBIT.getDockerClient()
                .restartContainerCmd(RABBIT.getContainerId())
                .withTimeout(10)
                .exec();

        await()
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() ->
                        assertThat(RABBIT.execInContainer("rabbitmq-diagnostics", "-q", "ping").getExitCode())
                                .isZero());

        await()
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(20))
                .ignoreExceptionsInstanceOf(AmqpException.class)
                .untilAsserted(() -> assertThat(taskQueueMessageCount()).isEqualTo(1));

        assertThat(rabbit.receiveAndConvert(RabbitTopology.TASK_QUEUE))
                .isEqualTo(new TaskMessage(taskId));
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
        TaskRecord task = queuedTask("CALLBACK", 3, 0, TaskFixtures.WIREMOCK_PAYLOAD);
        worker.process(task.getTaskId());
        worker.process(task.getTaskId());
        worker.process(task.getTaskId());
        TaskRecord completed = tasks.findById(task.getTaskId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(completed.getAttemptCount()).isEqualTo(3);
        WIREMOCK.verify(3, postRequestedFor(urlEqualTo("/execute")));
    }

    @Test
    void persistentBusinessFailureEndsInDeadState() {
        TaskRecord task = queuedTask("REPORT", 2, 0, TaskFixtures.PERMANENT_FAILURE_PAYLOAD);
        worker.process(task.getTaskId());
        worker.process(task.getTaskId());
        TaskRecord failed = tasks.findById(task.getTaskId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(failed.getFailureReason()).contains("Permanent business failure");
    }

    private TaskRecord queuedTask(String type, int maxAttempts, int failures, String payload) {
        return tasks.save(TaskFixtures.queued(TaskFixtures.key("worker-"), type, payload, maxAttempts, failures));
    }

    private OutboxEvent publishedEvent(String taskId) {
        return outbox.findAll().stream()
                .filter(event -> event.getAggregateId().equals(taskId))
                .findFirst()
                .orElseThrow();
    }

    private void purgeRabbitQueues() {
        rabbit.execute(channel -> {
            channel.queuePurge(RabbitTopology.RETRY_QUEUE);
            channel.queuePurge(RabbitTopology.DEAD_LETTER_QUEUE);
            channel.queuePurge(RabbitTopology.TASK_QUEUE);
            return null;
        });
    }

    private int taskQueueMessageCount() {
        Integer count = rabbit.execute(channel ->
                channel.queueDeclarePassive(RabbitTopology.TASK_QUEUE).getMessageCount());
        if (count == null) {
            throw new IllegalStateException("RabbitMQ did not return a task queue message count");
        }
        return count;
    }

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
