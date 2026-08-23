package io.github.asyncflow.framework;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

public class AsyncFlowApiClient {
    private final int port;

    public AsyncFlowApiClient(int port) {
        this.port = port;
    }

    public Response submit(String idempotencyKey, String body) {
        return given().port(port).contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey).body(body)
                .when().post("/api/tasks");
    }

    public Response getTask(String taskId) {
        return given().port(port).when().get("/api/tasks/{taskId}", taskId);
    }

    public Response downloadResult(String taskId) {
        return given().port(port).when().get("/api/tasks/{taskId}/result", taskId);
    }
}
