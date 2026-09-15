package io.github.asyncflow.service;

import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.framework.client.AsyncFlowApiClient;
import io.github.asyncflow.framework.data.TaskFixtures;
import io.github.asyncflow.framework.extension.AsyncFlowSupport;
import io.github.asyncflow.idempotency.IdempotencyStore;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@AsyncFlowSupport
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskSubmissionRollbackIntegrationTest {
    @LocalServerPort int port;
    @Autowired TaskRepository tasks;
    @Autowired TaskEventRepository events;
    @Autowired IdempotencyStore idempotency;

    @MockitoBean OutboxEventRepository outbox;

    @Test
    void outboxFailureRollsBackDatabaseAndReleasesReservation() {
        String key = TaskFixtures.key("outbox-failure-");
        when(outbox.save(any(OutboxEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("outbox unavailable"));

        Response response = new AsyncFlowApiClient(port)
                .submit(key, TaskFixtures.simulationBody());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(tasks.findByIdempotencyKey(key)).isEmpty();
        assertThat(events.count()).isZero();
        assertThat(idempotency.getTaskId(key)).isEmpty();
        verify(outbox).save(any(OutboxEvent.class));
    }
}
