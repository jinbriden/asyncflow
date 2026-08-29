package io.github.asyncflow.framework.scenario;

import io.github.asyncflow.framework.assertion.ApiAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.restassured.response.Response;

public class ReportBusinessScenario {
    private final AsyncFlowApiClient api;
    private final WorkerScenario worker;

    public ReportBusinessScenario(AsyncFlowApiClient api, WorkerScenario worker) {
        this.api = api;
        this.worker = worker;
    }

    public String submitAccepted(String key, String body) {
        Response response = api.submit(key, body);
        ApiAssertions.assertStatus(response, 202);
        return ApiAssertions.taskId(response);
    }

    public String submitAndProcess(String key, String body) {
        String taskId = submitAccepted(key, body);
        worker.queueAndProcess(taskId);
        return taskId;
    }

    public WorkerScenario worker() {
        return worker;
    }
}
