package io.github.asyncflow.business;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.framework.AsyncFlowApiClient;
import io.github.asyncflow.messaging.TaskMessage;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.UUID;

import static io.github.asyncflow.framework.ReportTestDataFactory.emptyRecordsBody;
import static io.github.asyncflow.framework.ReportTestDataFactory.validReportBody;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("business")
@Tag("regression")
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

    @BeforeEach
    void setUp() {
        api = new AsyncFlowApiClient(port);
        reportResults.deleteAll();
        events.deleteAll();
        outbox.deleteAll();
        tasks.deleteAll();
    }

    @Test
    @Tag("smoke")
    void submittedSalesDataProducesQueryableAndDownloadableCsvReport() {
        Response submitted = api.submit(key(), validReportBody("regional-sales"));
        submitted.then().statusCode(202);
        String taskId = submitted.path("taskId");

        queueAndProcess(taskId);

        Response task = api.getTask(taskId);
        task.then().statusCode(200);
        assertThat(task.path("status").toString()).isEqualTo("SUCCEEDED");
        assertThat((Integer) task.path("result.sourceRecordCount")).isEqualTo(3);
        assertThat((Integer) task.path("result.summaryRowCount")).isEqualTo(2);
        assertThat((Integer) task.path("result.totalQuantity")).isEqualTo(6);
        assertThat(new BigDecimal(task.path("result.totalAmount").toString()))
                .isEqualByComparingTo("1967.70");
        assertThat(task.path("result.sha256").toString()).hasSize(64);
        assertThat(task.path("result.downloadUrl").toString()).isEqualTo("/api/tasks/" + taskId + "/result");

        Response download = api.downloadResult(taskId);
        download.then().statusCode(200);
        assertThat(download.header("Content-Type")).startsWith("text/csv");
        assertThat(download.header("Content-Disposition")).contains("regional-sales.csv");
        String csv = new String(download.asByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("region,order_count,total_quantity,total_amount")
                .contains("East,2,3,1698.00")
                .contains("West,1,3,269.70")
                .contains("TOTAL,3,6,1967.70");
    }

    @Test
    void duplicateSubmissionReturnsSameTaskAndOneBusinessResult() {
        String key = key();
        String taskId = api.submit(key, validReportBody()).then().statusCode(202).extract().path("taskId");
        Response duplicate = api.submit(key, validReportBody());
        duplicate.then().statusCode(200);
        assertThat(duplicate.path("taskId").toString()).isEqualTo(taskId);

        queueAndProcess(taskId);
        processor.process(new TaskMessage(taskId));

        assertThat(reportResults.count()).isEqualTo(1);
        assertThat(tasks.findById(taskId).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void malformedReportIsRejectedBeforeTaskCreation() {
        Response response = api.submit(key(), emptyRecordsBody());
        response.then().statusCode(400);
        assertThat(response.path("code").toString()).isEqualTo("INVALID_REPORT_PAYLOAD");
        assertThat(tasks.count()).isZero();
    }

    @Test
    void reportCannotBeDownloadedBeforeWorkerCompletesIt() {
        String taskId = api.submit(key(), validReportBody()).then().statusCode(202).extract().path("taskId");
        Response response = api.downloadResult(taskId);
        response.then().statusCode(409);
        assertThat(response.path("code").toString()).isEqualTo("REPORT_NOT_READY");
    }

    private void queueAndProcess(String taskId) {
        TaskRecord task = tasks.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        task.queue();
        tasks.saveAndFlush(task);
        processor.process(new TaskMessage(taskId));
    }

    private String key() {
        return "report-business-" + UUID.randomUUID();
    }
}
