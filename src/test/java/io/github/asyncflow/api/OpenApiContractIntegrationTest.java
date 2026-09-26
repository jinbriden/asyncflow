package io.github.asyncflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractIntegrationTest {
    @LocalServerPort
    int port;

    @MockitoBean
    RabbitTemplate rabbit;

    @Test
    void frontendContractDocumentsOnlySupportedBusinessOperations() {
        given()
                .port(port)
        .when()
                .get("/v3/api-docs/frontend")
        .then()
                .statusCode(200)
                .body("info.title", equalTo("AsyncFlow Frontend API"))
                .body("paths", hasKey("/api/tasks"))
                .body("paths", hasKey("/api/tasks/{taskId}"))
                .body("paths", hasKey("/api/tasks/{taskId}/events"))
                .body("paths", hasKey("/api/tasks/{taskId}/result"))
                .body("paths", hasKey("/api/tasks/{taskId}/cancel"))
                .body("paths", not(hasKey("/api/tasks/{taskId}/compensate")))
                .body("paths", not(hasKey("/internal/tasks/{taskId}/retry")));
    }

    @Test
    void frontendContractUsesTypedReportPayloadAndHidesFaultInjection() {
        given()
                .port(port)
        .when()
                .get("/v3/api-docs/frontend")
        .then()
                .statusCode(200)
                .body("components.schemas.CreateTaskRequest.properties.payload.$ref",
                        endsWith("/ReportTaskPayload"))
                .body("components.schemas.CreateTaskRequest.properties", not(hasKey("simulateFailures")))
                .body("components.schemas.ReportTaskPayload.required.size()", equalTo(3))
                .body("components.schemas.ReportTaskPayload.properties.reportName.minLength", equalTo(1))
                .body("components.schemas.ReportTaskPayload.properties.requestedBy.minLength", equalTo(1))
                .body("components.schemas.ReportTaskPayload.properties.records.minItems", equalTo(1))
                .body("components.schemas.SalesRecord.required.size()", equalTo(5))
                .body("components.schemas.SalesRecord.properties.orderId.minLength", equalTo(1))
                .body("components.schemas.SalesRecord.properties.region.minLength", equalTo(1))
                .body("components.schemas.SalesRecord.properties.product.minLength", equalTo(1))
                .body("components.schemas.SalesRecord.properties.unitPrice.minimum", equalTo(0))
                .body("components.schemas.SalesRecord.properties.unitPrice.exclusiveMinimum", equalTo(0));
    }

    @Test
    void submitContractDocumentsHeadersAndTypedResponses() {
        given()
                .port(port)
        .when()
                .get("/v3/api-docs/frontend")
        .then()
                .statusCode(200)
                .body("paths.'/api/tasks'.post.parameters.find { it.name == 'Idempotency-Key' }.required",
                        equalTo(true))
                .body("paths.'/api/tasks'.post.parameters.find { it.name == 'Idempotency-Key' }.schema.minLength",
                        equalTo(1))
                .body("paths.'/api/tasks'.post.parameters.name", not(hasItem("X-Internal-Token")))
                .body("paths.'/api/tasks'.post.responses.'200'.content.'application/json'.schema.$ref",
                        endsWith("/TaskResponse"))
                .body("paths.'/api/tasks'.post.responses.'202'.content.'application/json'.schema.$ref",
                        endsWith("/TaskResponse"))
                .body("paths.'/api/tasks'.post.responses.'400'.content.'application/json'.schema.$ref",
                        endsWith("/ApiError"))
                .body("paths.'/api/tasks'.post.responses.'202'.headers.'X-Trace-Id'.schema.type",
                        equalTo("string"));
    }

    @Test
    void responseSchemasKeepRuntimeFieldsRequired() {
        given()
                .port(port)
        .when()
                .get("/v3/api-docs/frontend")
        .then()
                .statusCode(200)
                .body("components.schemas.TaskResponse.required.size()", equalTo(10))
                .body("components.schemas.TaskResponse.properties.result.anyOf[0].$ref",
                        endsWith("/ReportResultResponse"))
                .body("components.schemas.TaskResponse.properties.result.anyOf[1].type", equalTo("null"))
                .body("components.schemas.TaskEventResponse.required.size()", equalTo(6))
                .body("components.schemas.TaskEventResponse.properties.fromStatus.anyOf[0].type", equalTo("string"))
                .body("components.schemas.TaskEventResponse.properties.fromStatus.anyOf[1].type", equalTo("null"))
                .body("components.schemas.ReportResultResponse.required.size()", equalTo(12))
                .body("components.schemas.ApiError.required.size()", equalTo(4));
    }

    @Test
    void operationsDocumentCsvAndStructuredErrors() {
        given()
                .port(port)
        .when()
                .get("/v3/api-docs/frontend")
        .then()
                .statusCode(200)
                .body("paths.'/api/tasks/{taskId}'.get.responses.'404'.content.'application/json'.schema.$ref",
                        endsWith("/ApiError"))
                .body("paths.'/api/tasks/{taskId}/cancel'.post.responses.'409'.content.'application/json'.schema.$ref",
                        endsWith("/ApiError"))
                .body("paths.'/api/tasks'.get.responses.'200'.content", hasKey("application/json"))
                .body("paths.'/api/tasks/{taskId}/events'.get.responses.'200'.content", hasKey("application/json"))
                .body("paths.'/api/tasks/{taskId}/result'.get.responses.'200'.content.'text/csv'.schema.type",
                        equalTo("string"))
                .body("paths.'/api/tasks/{taskId}/result'.get.responses.'200'.content.'text/csv'.schema.format",
                        equalTo("binary"))
                .body("paths.'/api/tasks/{taskId}/result'.get.responses.'404'.content.'application/json'.schema.$ref",
                        endsWith("/ApiError"))
                .body("paths.'/api/tasks/{taskId}/result'.get.responses.'409'.content.'application/json'.schema.$ref",
                        endsWith("/ApiError"));
    }

    @Test
    void defaultContractDoesNotExposeInternalOperations() {
        given()
                .port(port)
        .when()
                .get("/v3/api-docs")
        .then()
                .statusCode(200)
                .body("paths", not(hasKey("/internal/tasks/{taskId}/retry")))
                .body("paths", not(hasKey("/internal/tasks/{taskId}/compensate")))
                .body("paths", not(hasKey("/api/tasks/{taskId}/compensate")));
    }
}
