package io.github.asyncflow.framework.scenario;

import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentSubmitScenario {
    public record Result(Set<String> taskIds, int duplicateResponses) {
    }

    public Result submitSameKey(AsyncFlowApiClient api, String key, String body, int total, int poolSize)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return api.submit(key, body);
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            int duplicates = 0;
            for (Future<Response> future : futures) {
                Response response = future.get();
                ids.add(response.path("taskId"));
                if (Boolean.TRUE.equals(response.path("deduplicated"))) {
                    duplicates++;
                }
            }
            return new Result(ids, duplicates);
        } finally {
            pool.shutdownNow();
        }
    }
}
