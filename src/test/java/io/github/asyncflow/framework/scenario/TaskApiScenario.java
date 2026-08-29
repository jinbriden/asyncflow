package io.github.asyncflow.framework.scenario;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.framework.assertion.ApiAssertions;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.client.StoreClient;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.restassured.response.Response;

public class TaskApiScenario {
    private final AsyncFlowApiClient api;
    private final StoreClient store;

    public TaskApiScenario(AsyncFlowApiClient api, StoreClient store) {
        this.api = api;
        this.store = store;
    }

    public String submitCreated(String key, String body) {
        Response response = api.submit(key, body);
        ApiAssertions.assertStatus(response, 202);
        return ApiAssertions.taskId(response);
    }

    public String submitThenCancel(String key, String body) {
        String taskId = submitCreated(key, body);
        ApiAssertions.assertCancelled(api.cancel(taskId));
        return taskId;
    }

    public TaskRecord saveDeadTask(String key) {
        return store.saveTask(TaskFixtures.deadTask(key));
    }
}
