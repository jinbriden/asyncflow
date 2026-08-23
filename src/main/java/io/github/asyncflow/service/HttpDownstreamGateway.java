package io.github.asyncflow.service;

import io.github.asyncflow.domain.TaskRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class HttpDownstreamGateway implements DownstreamGateway {
    private final RestClient client;

    public HttpDownstreamGateway(RestClient.Builder builder,
                                 @Value("${asyncflow.downstream.base-url:http://localhost:18089}") String baseUrl,
                                 @Value("${asyncflow.downstream.timeout:PT1S}") Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public void execute(TaskRecord task) {
        if (!"CALLBACK".equalsIgnoreCase(task.getTaskType())) return;
        client.post().uri("/execute")
                .header("X-Task-Id", task.getTaskId())
                .body(task.getPayload())
                .retrieve()
                .toBodilessEntity();
    }
}
