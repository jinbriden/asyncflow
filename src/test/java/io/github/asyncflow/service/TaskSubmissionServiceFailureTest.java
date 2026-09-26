package io.github.asyncflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.asyncflow.api.CreateTaskRequest;
import io.github.asyncflow.api.TaskResponse;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.idempotency.IdempotencyStore;
import io.github.asyncflow.report.ReportPayloadParser;
import io.github.asyncflow.repository.TaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskSubmissionServiceFailureTest {
    private static final String KEY = "same-key";

    @Mock TaskRepository tasks;
    @Mock IdempotencyStore idempotency;
    @Mock ReportPayloadParser reportPayloadParser;
    @Mock TaskCreationTransaction creation;

    private TaskSubmissionService service;
    private CreateTaskRequest request;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new TaskSubmissionService(tasks, idempotency, objectMapper,
                new SimpleMeterRegistry(), reportPayloadParser, creation);
        request = new CreateTaskRequest("SIMULATION", objectMapper.createObjectNode(), 3, 0);
        when(idempotency.getTaskId(KEY)).thenReturn(Optional.empty());
        when(idempotency.reserve(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void databaseConflictReleasesReservationAndReturnsConcurrentWinner() {
        TaskRecord winner = TaskRecord.create(KEY, "SIMULATION", "{}", 3, 0);
        when(tasks.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        doThrow(new DataIntegrityViolationException("duplicate idempotency key"))
                .when(creation).create(any(TaskRecord.class), anyString());

        TaskResponse response = service.submit(KEY, request);

        assertThat(response.taskId()).isEqualTo(winner.getTaskId());
        assertThat(response.deduplicated()).isTrue();
        ArgumentCaptor<String> losingTaskId = ArgumentCaptor.forClass(String.class);
        verify(idempotency).release(eq(KEY), losingTaskId.capture());
        assertThat(losingTaskId.getValue()).isNotEqualTo(winner.getTaskId());
        verify(creation).create(any(TaskRecord.class), anyString());
    }

    @Test
    void databaseConflictWithoutWinnerReleasesReservationAndRethrows() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("duplicate idempotency key");
        when(tasks.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        doThrow(failure).when(creation).create(any(TaskRecord.class), anyString());

        assertThatThrownBy(() -> service.submit(KEY, request)).isSameAs(failure);

        verify(idempotency).release(eq(KEY), anyString());
        verify(creation).create(any(TaskRecord.class), anyString());
    }

    @Test
    void databaseConflictLookupFailurePreservesOriginalException() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("duplicate idempotency key");
        IllegalStateException lookupFailure = new IllegalStateException("database unavailable");
        when(tasks.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenThrow(lookupFailure);
        doThrow(failure).when(creation).create(any(TaskRecord.class), anyString());

        assertThatThrownBy(() -> service.submit(KEY, request))
                .isSameAs(failure)
                .hasSuppressedException(lookupFailure);

        verify(idempotency).release(eq(KEY), anyString());
    }

    @Test
    void reservationCleanupFailureDoesNotMaskOriginalException() {
        IllegalStateException failure = new IllegalStateException("outbox unavailable");
        IllegalStateException cleanupFailure = new IllegalStateException("redis unavailable");
        when(tasks.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        doThrow(failure).when(creation).create(any(TaskRecord.class), anyString());
        doThrow(cleanupFailure).when(idempotency).release(eq(KEY), anyString());

        assertThatThrownBy(() -> service.submit(KEY, request))
                .isSameAs(failure)
                .hasSuppressedException(cleanupFailure);
    }

    @Test
    void failureAfterReservationReleasesReservationAndPreservesOriginalException() {
        IllegalStateException failure = new IllegalStateException("outbox unavailable");
        when(tasks.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        doThrow(failure).when(creation).create(any(TaskRecord.class), anyString());

        assertThatThrownBy(() -> service.submit(KEY, request)).isSameAs(failure);

        verify(idempotency).release(eq(KEY), anyString());
        verify(creation).create(any(TaskRecord.class), anyString());
    }
}
