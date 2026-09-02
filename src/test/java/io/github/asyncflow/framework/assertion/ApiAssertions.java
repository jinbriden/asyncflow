package io.github.asyncflow.framework.assertion;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public final class ApiAssertions {
    private ApiAssertions() {
    }

    public static ValidatableResponse assertStatus(Response response, int status) {
        return response.then().statusCode(status);
    }

    public static ValidatableResponse assertAccepted(Response response) {
        return response.then().statusCode(202)
                .header("Location", startsWith("/api/tasks/"))
                .body("taskId", notNullValue())
                .body("status", equalTo("CREATED"))
                .body("deduplicated", equalTo(false));
    }

    public static ValidatableResponse assertDeduplicated(Response response, String taskId) {
        return response.then().statusCode(200)
                .body("taskId", equalTo(taskId))
                .body("deduplicated", equalTo(true));
    }

    public static ValidatableResponse assertTask(Response response, String taskId, int attemptCount) {
        return response.then().statusCode(200)
                .body("taskId", equalTo(taskId))
                .body("attemptCount", equalTo(attemptCount));
    }

    public static ValidatableResponse assertErrorCode(Response response, int status, String code) {
        return response.then().statusCode(status).body("code", equalTo(code));
    }

    public static ValidatableResponse assertCancelled(Response response) {
        return response.then().statusCode(200).body("status", equalTo("CANCELLED"));
    }

    public static ValidatableResponse assertQueued(Response response) {
        return response.then().statusCode(200).body("status", equalTo("QUEUED"));
    }

    public static ValidatableResponse assertCompensated(Response response) {
        return response.then().statusCode(200).body("status", equalTo("COMPENSATED"));
    }

    public static ValidatableResponse assertCreatedThenCancelledEvents(Response response) {
        return response.then().statusCode(200).body("$", hasSize(2))
                .body("[0].toStatus", equalTo("CREATED"))
                .body("[1].toStatus", equalTo("CANCELLED"));
    }

    public static ValidatableResponse assertListTotal(Response response, int totalElements) {
        return response.then().statusCode(200).body("page.totalElements", equalTo(totalElements));
    }

    public static ValidatableResponse assertPageSizeAtLeast(Response response, int minimum) {
        return response.then().statusCode(200).body("page.size", greaterThanOrEqualTo(minimum));
    }

    public static ValidatableResponse assertTraceId(Response response, int status, String traceId) {
        return response.then().statusCode(status).header("X-Trace-Id", equalTo(traceId));
    }

    public static String taskId(Response response) {
        return response.path("taskId");
    }
}
