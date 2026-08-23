package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static io.github.asyncflow.framework.ReportTestDataFactory.validReportBody;

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

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        events.deleteAll();
        outbox.deleteAll();
        tasks.deleteAll();
    }

    @Test
    @Tag("smoke")
    void submitReturnsAcceptedAndLocation() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", key())
                .body(validBody())
        .when().post("/api/tasks")
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
        given().contentType(ContentType.JSON).header("Idempotency-Key", key).body(validBody())
                .when().post("/api/tasks")
                .then().statusCode(200).body("taskId", equalTo(taskId)).body("deduplicated", equalTo(true));
        org.assertj.core.api.Assertions.assertThat(tasks.count()).isEqualTo(1);
    }

    @Test
    void queryReturnsPersistedState() {
        String taskId = submit(key());
        given().when().get("/api/tasks/{id}", taskId)
                .then().statusCode(200).body("taskId", equalTo(taskId)).body("attemptCount", equalTo(0));
    }

    @Test
    void unknownTaskReturnsStructured404() {
        given().when().get("/api/tasks/missing")
                .then().statusCode(404).body("code", equalTo("TASK_NOT_FOUND"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        given().contentType(ContentType.JSON).body(validBody())
                .when().post("/api/tasks").then().statusCode(400);
    }

    @Test
    void blankTaskTypeIsRejected() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", key())
                .body("{\"type\":\"\",\"payload\":{}}")
                .when().post("/api/tasks").then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void zeroMaxAttemptsIsRejected() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", key())
                .body("{\"type\":\"REPORT\",\"payload\":{},\"maxAttempts\":0}")
                .when().post("/api/tasks").then().statusCode(400);
    }

    @Test
    void excessiveFailureInjectionIsRejected() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", key())
                .body("{\"type\":\"REPORT\",\"payload\":{},\"simulateFailures\":11}")
                .when().post("/api/tasks").then().statusCode(400);
    }

    @Test
    void createdTaskCanBeCancelled() {
        String taskId = submit(key());
        given().when().post("/api/tasks/{id}/cancel", taskId)
                .then().statusCode(200).body("status", equalTo("CANCELLED"));
    }

    @Test
    void cancellingTerminalTaskReturnsConflict() {
        String taskId = submit(key());
        given().when().post("/api/tasks/{id}/cancel", taskId).then().statusCode(200);
        given().when().post("/api/tasks/{id}/cancel", taskId)
                .then().statusCode(409).body("code", equalTo("INVALID_TASK_STATE"));
    }

    @Test
    void eventTimelineIsAppendOnlyAndOrdered() {
        String taskId = submit(key());
        given().when().post("/api/tasks/{id}/cancel", taskId).then().statusCode(200);
        given().when().get("/api/tasks/{id}/events", taskId)
                .then().statusCode(200).body("$", hasSize(2))
                .body("[0].toStatus", equalTo("CREATED"))
                .body("[1].toStatus", equalTo("CANCELLED"));
    }

    @Test
    void listCanFilterByStatus() {
        String taskId = submit(key());
        given().when().post("/api/tasks/{id}/cancel", taskId).then().statusCode(200);
        given().queryParam("status", "CANCELLED").when().get("/api/tasks")
                .then().statusCode(200).body("page.totalElements", equalTo(1));
    }

    @Test
    void internalRetryRequeuesDeadTask() {
        TaskRecord task = deadTask();
        tasks.save(task);
        given().header("X-Internal-Token", "change-me").when().post("/internal/tasks/{id}/retry", task.getTaskId())
                .then().statusCode(200).body("status", equalTo("QUEUED"));
    }

    @Test
    void internalRetryRequiresToken() {
        TaskRecord task = deadTask();
        tasks.save(task);
        given().when().post("/internal/tasks/{id}/retry", task.getTaskId())
                .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void compensationClosesDeadTask() {
        TaskRecord task = deadTask();
        tasks.save(task);
        given().when().post("/api/tasks/{id}/compensate", task.getTaskId())
                .then().statusCode(200).body("status", equalTo("COMPENSATED"));
    }

    @Test
    void pageSizeIsBoundedButNeverZero() {
        submit(key());
        given().queryParam("size", 0).when().get("/api/tasks")
                .then().statusCode(200).body("page.size", greaterThanOrEqualTo(1));
    }

    @Test
    void traceIdIsEchoedForFailureEvidenceCorrelation() {
        given().header("X-Trace-Id", "trace-integration-001")
                .when().get("/api/tasks/missing")
                .then().statusCode(404).header("X-Trace-Id", equalTo("trace-integration-001"));
    }

    private String submit(String key) {
        return given().contentType(ContentType.JSON).header("Idempotency-Key", key).body(validBody())
                .when().post("/api/tasks").then().statusCode(202).extract().path("taskId");
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
