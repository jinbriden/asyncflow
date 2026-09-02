package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.framework.assertion.ApiAssertions;
import io.github.asyncflow.framework.assertion.StoreAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.client.StoreClient;
import io.github.asyncflow.framework.data.ReportTestDataFactory;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.framework.scenario.TaskApiScenario;
import io.github.asyncflow.report.ReportResultRepository;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static io.github.asyncflow.framework.client.AsyncFlowApiClient.DEFAULT_INTERNAL_TOKEN;
import static io.github.asyncflow.framework.data.ReportTestDataFactory.validReportBody;
import static io.github.asyncflow.framework.data.TaskFixtures.TRACE_ID;

@Tag("regression")
@AsyncFlowSupport
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskApiIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;
    @Autowired ReportResultRepository reports;

    @MockitoBean RabbitTemplate rabbit;

    private AsyncFlowApiClient api;
    private StoreClient store;
    private TaskApiScenario scenario;

    @BeforeEach
    void setUp() {
        api = new AsyncFlowApiClient(port);
        store = new StoreClient(tasks, events, outbox, reports);
        scenario = new TaskApiScenario(api, store);
    }

    @Test
    @Tag("smoke")
    void submitReturnsAcceptedAndLocation() {
        ApiAssertions.assertAccepted(api.submit(key(), validBody()));
    }

    @Test
    void repeatedRequestReturnsSameTask() {
        String key = key();
        String taskId = scenario.submitCreated(key, validBody());
        ApiAssertions.assertDeduplicated(api.submit(key, validBody()), taskId);
        StoreAssertions.assertTaskCount(store, 1);
    }

    @Test
    void queryReturnsPersistedState() {
        String taskId = scenario.submitCreated(key(), validBody());
        ApiAssertions.assertTask(api.getTask(taskId), taskId, 0);
    }

    @Test
    void unknownTaskReturnsStructured404() {
        ApiAssertions.assertErrorCode(api.getTask("missing"), 404, "TASK_NOT_FOUND");
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        ApiAssertions.assertStatus(api.submit(null, validBody()), 400);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.github.asyncflow.framework.cases.CaseLoader#submitValidation")
    void submitIsRejectedForInvalidPayload(String name, String body, int status, String code) {
        Response response = api.submit(key(), body);
        if (code == null) {
            ApiAssertions.assertStatus(response, status);
        } else {
            ApiAssertions.assertErrorCode(response, status, code);
        }
    }

    @Test
    void createdTaskCanBeCancelled() {
        String taskId = scenario.submitCreated(key(), validBody());
        ApiAssertions.assertCancelled(api.cancel(taskId));
    }

    @Test
    void cancellingTerminalTaskReturnsConflict() {
        String taskId = scenario.submitThenCancel(key(), validBody());
        ApiAssertions.assertErrorCode(api.cancel(taskId), 409, "INVALID_TASK_STATE");
    }

    @Test
    void eventTimelineIsAppendOnlyAndOrdered() {
        String taskId = scenario.submitThenCancel(key(), validBody());
        ApiAssertions.assertCreatedThenCancelledEvents(api.events(taskId));
    }

    @Test
    void listCanFilterByStatus() {
        scenario.submitThenCancel(key(), validBody());
        ApiAssertions.assertListTotal(api.listTasksByStatus("CANCELLED"), 1);
    }

    @Test
    void internalRetryRequeuesDeadTask() {
        TaskRecord task = scenario.saveDeadTask(key());
        ApiAssertions.assertQueued(api.retry(task.getTaskId(), DEFAULT_INTERNAL_TOKEN));
    }

    @Test
    void internalRetryRequiresToken() {
        TaskRecord task = scenario.saveDeadTask(key());
        ApiAssertions.assertErrorCode(api.retry(task.getTaskId(), null), 401, "UNAUTHORIZED");
    }

    @Test
    void compensationClosesDeadTask() {
        TaskRecord task = scenario.saveDeadTask(key());
        ApiAssertions.assertCompensated(api.compensate(task.getTaskId()));
    }

    @Test
    void pageSizeIsBoundedButNeverZero() {
        scenario.submitCreated(key(), validBody());
        ApiAssertions.assertPageSizeAtLeast(api.listTasksBySize(0), 1);
    }

    @Test
    void traceIdIsEchoedForFailureEvidenceCorrelation() {
        ApiAssertions.assertTraceId(api.getTask("missing", TRACE_ID), 404, TRACE_ID);
    }

    private String key() {
        return TaskFixtures.key("it-");
    }

    private String validBody() {
        return validReportBody(ReportTestDataFactory.INTEGRATION_REPORT_NAME);
    }
}
