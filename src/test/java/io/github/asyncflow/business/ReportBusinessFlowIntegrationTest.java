package io.github.asyncflow.business;

import io.github.asyncflow.framework.assertion.ApiAssertions;
import io.github.asyncflow.framework.assertion.ReportAssertions;
import io.github.asyncflow.framework.assertion.StoreAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.client.StoreClient;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.framework.scenario.ReportBusinessScenario;
import io.github.asyncflow.framework.scenario.WorkerScenario;
import io.github.asyncflow.report.ReportResultRepository;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.service.TaskProcessor;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static io.github.asyncflow.framework.data.ReportTestDataFactory.REGIONAL_SALES_NAME;
import static io.github.asyncflow.framework.data.ReportTestDataFactory.emptyRecordsBody;
import static io.github.asyncflow.framework.data.ReportTestDataFactory.validReportBody;

@Tag("business")
@Tag("regression")
@AsyncFlowSupport
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportBusinessFlowIntegrationTest {
    @TempDir static Path reportDirectory;

    @DynamicPropertySource
    static void reportStorage(DynamicPropertyRegistry registry) {
        registry.add("asyncflow.report.storage-directory", reportDirectory::toString);
    }

    @LocalServerPort int port;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired OutboxEventRepository outbox;
    @Autowired ReportResultRepository reportResults;
    @Autowired TaskProcessor processor;
    @MockitoBean RabbitTemplate rabbit;

    private AsyncFlowApiClient api;
    private StoreClient store;
    private ReportBusinessScenario reports;

    @BeforeEach
    void setUp() {
        api = new AsyncFlowApiClient(port);
        store = new StoreClient(tasks, events, outbox, reportResults);
        reports = new ReportBusinessScenario(api, new WorkerScenario(tasks, processor));
    }

    @Test
    @Tag("smoke")
    void submittedSalesDataProducesQueryableAndDownloadableCsvReport() {
        String taskId = reports.submitAndProcess(key(), validReportBody(REGIONAL_SALES_NAME));
        ReportAssertions.assertQueryableRegionalSales(api.getTask(taskId), taskId);
        ReportAssertions.assertDownloadableRegionalSalesCsv(api.downloadResult(taskId));
    }

    @Test
    void duplicateSubmissionReturnsSameTaskAndOneBusinessResult() {
        String key = key();
        String taskId = reports.submitAccepted(key, validReportBody());
        ApiAssertions.assertDeduplicated(api.submit(key, validReportBody()), taskId);

        reports.worker().queueAndProcess(taskId);
        reports.worker().process(taskId);

        StoreAssertions.assertReportResultCount(store, 1);
        StoreAssertions.assertAttemptCount(store, taskId, 1);
    }

    @Test
    void malformedReportIsRejectedBeforeTaskCreation() {
        Response response = api.submit(key(), emptyRecordsBody());
        ApiAssertions.assertErrorCode(response, 400, "INVALID_REPORT_PAYLOAD");
        StoreAssertions.assertNoTasks(store);
    }

    @Test
    void reportCannotBeDownloadedBeforeWorkerCompletesIt() {
        String taskId = reports.submitAccepted(key(), validReportBody());
        ApiAssertions.assertErrorCode(api.downloadResult(taskId), 409, "REPORT_NOT_READY");
    }

    private String key() {
        return TaskFixtures.key("report-business-");
    }
}
