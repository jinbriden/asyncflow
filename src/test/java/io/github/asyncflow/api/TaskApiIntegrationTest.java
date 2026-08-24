package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.framework.AsyncFlowApiClient;
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

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static io.github.asyncflow.framework.ReportTestDataFactory.blankTypeBody;
import static io.github.asyncflow.framework.ReportTestDataFactory.excessiveFailureInjectionBody;
import static io.github.asyncflow.framework.ReportTestDataFactory.validReportBody;
import static io.github.asyncflow.framework.ReportTestDataFactory.zeroMaxAttemptsBody;

@Tag("regression")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskApiIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;

    @MockitoBean RabbitTemplate rabbit;

    private AsyncFlowApiClient api;

    @BeforeEach
    void setUp() {
        api = new AsyncFlowApiClient(port);
        events.deleteAll();
        outbox.deleteAll();
        tasks.deleteAll();
    }

    @Test
    @Tag("smoke")
    void submitReturnsAcceptedAndLocation() {
        api.submit(key(), validBody())
        .then().statusCode(202)
                .header("Location", org.hamcrest.Matchers.startsWith("/api/tasks/"))
                .body("taskId", notNullValue())
                .body("status", equalTo("CREATED"))
                .body("deduplicated", equalTo(false));
    }

    @Test
    void repeatedRequestReturnsSameTask() {
        String key = key();
        String taskId = submit(key);
        api.submit(key, validBody())
                .then().statusCode(200).body("taskId", equalTo(taskId)).body("deduplicated", equalTo(true));
        org.assertj.core.api.Assertions.assertThat(tasks.count()).isEqualTo(1);
    }

    @Test
    void queryReturnsPersistedState() {
        String taskId = submit(key());
        api.getTask(taskId)
                .then().statusCode(200).body("taskId", equalTo(taskId)).body("attemptCount", equalTo(0));
    }

    @Test
    void unknownTaskReturnsStructured404() {
        api.getTask("missing")
                .then().statusCode(404).body("code", equalTo("TASK_NOT_FOUND"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        api.submit(null, validBody()).then().statusCode(400);
    }

    @Test
    void blankTaskTypeIsRejected() {
        api.submit(key(), blankTypeBody())
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void zeroMaxAttemptsIsRejected() {
        api.submit(key(), zeroMaxAttemptsBody()).then().statusCode(400);
    }

    @Test
    void excessiveFailureInjectionIsRejected() {
        api.submit(key(), excessiveFailureInjectionBody()).then().statusCode(400);
    }

    @Test
    void createdTaskCanBeCancelled() {
        String taskId = submit(key());
        api.cancel(taskId)
                .then().statusCode(200).body("status", equalTo("CANCELLED"));
    }

    @Test
    void cancellingTerminalTaskReturnsConflict() {
        String taskId = submit(key());
        api.cancel(taskId).then().statusCode(200);
        api.cancel(taskId)
                .then().statusCode(409).body("code", equalTo("INVALID_TASK_STATE"));
    }

    @Test
    void eventTimelineIsAppendOnlyAndOrdered() {
        String taskId = submit(key());
        api.cancel(taskId).then().statusCode(200);
        api.events(taskId)
                .then().statusCode(200).body("$", hasSize(2))
                .body("[0].toStatus", equalTo("CREATED"))
                .body("[1].toStatus", equalTo("CANCELLED"));
    }

    @Test
    void listCanFilterByStatus() {
        String taskId = submit(key());
        api.cancel(taskId).then().statusCode(200);
        api.listTasksByStatus("CANCELLED")
                .then().statusCode(200).body("page.totalElements", equalTo(1));
    }

    @Test
    void internalRetryRequeuesDeadTask() {
        TaskRecord task = deadTask();
        tasks.save(task);
        api.retry(task.getTaskId(), "change-me")
                .then().statusCode(200).body("status", equalTo("QUEUED"));
    }

    @Test
    void internalRetryRequiresToken() {
        TaskRecord task = deadTask();
        tasks.save(task);
        api.retry(task.getTaskId(), null)
                .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void compensationClosesDeadTask() {
        TaskRecord task = deadTask();
        tasks.save(task);
        api.compensate(task.getTaskId())
                .then().statusCode(200).body("status", equalTo("COMPENSATED"));
    }

    @Test
    void pageSizeIsBoundedButNeverZero() {
        submit(key());
        api.listTasksBySize(0)
                .then().statusCode(200).body("page.size", greaterThanOrEqualTo(1));
    }

    @Test
    void traceIdIsEchoedForFailureEvidenceCorrelation() {
        api.getTask("missing", "trace-integration-001")
                .then().statusCode(404).header("X-Trace-Id", equalTo("trace-integration-001"));
    }

    private String submit(String key) {
        return api.submit(key, validBody()).then().statusCode(202).extract().path("taskId");
    }

    private TaskRecord deadTask() {
        TaskRecord task = TaskRecord.create(key(), "REPORT", "{\"runId\":\"dead\"}", 1, 1);
        task.queue();
        task.beginAttempt();
        task.markDeadLettered("injected");
        return task;
    }

    private String key() { return "it-" + UUID.randomUUID(); }
    private String validBody() {
        return validReportBody("integration-report");
    }
}
