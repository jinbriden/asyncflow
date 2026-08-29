package io.github.asyncflow.framework.client;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class AsyncFlowApiClient {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String DEFAULT_INTERNAL_TOKEN = "change-me";

    private final int port;

    public AsyncFlowApiClient(int port) {
        this.port = port;
    }

    public Response submit(String idempotencyKey, String body) {
        RequestSpecification spec = json();
        if (idempotencyKey != null) {
            spec.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        return record(spec.body(body).when().post("/api/tasks"));
    }

    public Response getTask(String taskId) {
        return getTask(taskId, null);
    }

    public Response getTask(String taskId, String traceId) {
        RequestSpecification spec = spec();
        if (traceId != null) {
            spec.header(TRACE_ID_HEADER, traceId);
        }
        return record(spec.when().get("/api/tasks/{taskId}", taskId));
    }

    public Response downloadResult(String taskId) {
        return record(spec().when().get("/api/tasks/{taskId}/result", taskId));
    }

    public Response cancel(String taskId) {
        return record(spec().when().post("/api/tasks/{taskId}/cancel", taskId));
    }

    public Response events(String taskId) {
        return record(spec().when().get("/api/tasks/{taskId}/events", taskId));
    }

    public Response listTasks() {
        return record(spec().when().get("/api/tasks"));
    }

    public Response listTasksByStatus(String status) {
        return record(spec().queryParam("status", status).when().get("/api/tasks"));
    }

    public Response listTasksBySize(int size) {
        return record(spec().queryParam("size", size).when().get("/api/tasks"));
    }

    public Response compensate(String taskId) {
        return record(spec().when().post("/api/tasks/{taskId}/compensate", taskId));
    }

    public Response retry(String taskId, String internalToken) {
        RequestSpecification spec = spec();
        if (internalToken != null) {
            spec.header(INTERNAL_TOKEN_HEADER, internalToken);
        }
        return record(spec.when().post("/internal/tasks/{taskId}/retry", taskId));
    }

    private RequestSpecification spec() {
        return given().port(port);
    }

    private RequestSpecification json() {
        return spec().contentType(ContentType.JSON);
    }

    private Response record(Response response) {
        String body = response.getContentType() != null && response.getContentType().contains("csv")
                ? "<csv " + response.asByteArray().length + " bytes>"
                : truncate(response.asString());
        CallRecorder.record(response.getStatusCode() + " " + body);
        return response;
    }

    private static String truncate(String body) {
        if (body == null || body.length() <= 2000) {
            return body;
        }
        return body.substring(0, 2000) + "...";
    }
}
