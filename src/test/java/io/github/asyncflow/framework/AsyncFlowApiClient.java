package io.github.asyncflow.framework;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class AsyncFlowApiClient {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final int port;

    public AsyncFlowApiClient(int port) {
        this.port = port;
    }

    public Response submit(String idempotencyKey, String body) {
        RequestSpecification spec = json();
        if (idempotencyKey != null) {
            spec.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        return spec.body(body).when().post("/api/tasks");
    }

    public Response getTask(String taskId) {
        return getTask(taskId, null);
    }

    public Response getTask(String taskId, String traceId) {
        RequestSpecification spec = spec();
        if (traceId != null) {
            spec.header(TRACE_ID_HEADER, traceId);
        }
        return spec.when().get("/api/tasks/{taskId}", taskId);
    }

    public Response downloadResult(String taskId) {
        return spec().when().get("/api/tasks/{taskId}/result", taskId);
    }

    public Response cancel(String taskId) {
        return spec().when().post("/api/tasks/{taskId}/cancel", taskId);
    }

    public Response events(String taskId) {
        return spec().when().get("/api/tasks/{taskId}/events", taskId);
    }

    public Response listTasks() {
        return spec().when().get("/api/tasks");
    }

    public Response listTasksByStatus(String status) {
        return spec().queryParam("status", status).when().get("/api/tasks");
    }

    public Response listTasksBySize(int size) {
        return spec().queryParam("size", size).when().get("/api/tasks");
    }

    public Response compensate(String taskId) {
        return spec().when().post("/api/tasks/{taskId}/compensate", taskId);
    }

    public Response retry(String taskId, String internalToken) {
        RequestSpecification spec = spec();
        if (internalToken != null) {
            spec.header(INTERNAL_TOKEN_HEADER, internalToken);
        }
        return spec.when().post("/internal/tasks/{taskId}/retry", taskId);
    }

    private RequestSpecification spec() {
        return given().port(port);
    }

    private RequestSpecification json() {
        return spec().contentType(ContentType.JSON);
    }
}
