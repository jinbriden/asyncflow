package io.github.asyncflow.service;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.TaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleTaskScannerTest {
    private TaskRepository tasks;
    private TaskEventRecorder recorder;
    private RabbitTemplate rabbit;
    private SimpleMeterRegistry metrics;
    private StaleTaskScanner scanner;

    @BeforeEach
    void setUp() {
        tasks = mock(TaskRepository.class);
        recorder = mock(TaskEventRecorder.class);
        rabbit = mock(RabbitTemplate.class);
        metrics = new SimpleMeterRegistry();
        scanner = new StaleTaskScanner(tasks, recorder, rabbit, metrics, Duration.ofMinutes(2));
    }

    @Test
    void recoverStaleRunningTasksRequeuesRetryableTask() {
        TaskRecord task = runningTask(3);
        when(tasks.findByStatusAndUpdatedAtBefore(eq(TaskStatus.RUNNING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        Instant scanStarted = Instant.now();

        scanner.recoverStaleRunningTasks();

        Instant scanFinished = Instant.now();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(tasks).findByStatusAndUpdatedAtBefore(eq(TaskStatus.RUNNING), cutoff.capture(), page.capture());
        assertThat(cutoff.getValue()).isBetween(
                scanStarted.minus(Duration.ofMinutes(2)),
                scanFinished.minus(Duration.ofMinutes(2)));
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RETRYING);
        verify(recorder).record(task, TaskStatus.RUNNING, "SCANNER", "Worker heartbeat timed out");
        verify(rabbit).convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.RETRY_KEY,
                new TaskMessage(task.getTaskId()));
        assertThat(metrics.counter("asyncflow.tasks.stale.recovered", "result", "RETRYING").count()).isEqualTo(1);
    }

    @Test
    void recoverStaleRunningTasksDeadLettersExhaustedTask() {
        TaskRecord task = runningTask(1);
        when(tasks.findByStatusAndUpdatedAtBefore(eq(TaskStatus.RUNNING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));

        scanner.recoverStaleRunningTasks();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DEAD);
        verify(recorder).record(task, TaskStatus.RUNNING, "SCANNER", "Retry budget exhausted");
        verify(rabbit).convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.DEAD_KEY,
                new TaskMessage(task.getTaskId()));
        assertThat(metrics.counter("asyncflow.tasks.stale.recovered", "result", "DEAD").count()).isEqualTo(1);
    }

    @Test
    void recoverStaleRunningTasksDoesNothingWhenNoneAreStale() {
        when(tasks.findByStatusAndUpdatedAtBefore(eq(TaskStatus.RUNNING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        scanner.recoverStaleRunningTasks();

        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
        verify(recorder, never()).record(any(), any(), any(), any());
    }

    private TaskRecord runningTask(int maxAttempts) {
        TaskRecord task = TaskRecord.create("scanner-" + maxAttempts, "REPORT", "{\"runId\":\"stale\"}", maxAttempts, 0);
        task.queue();
        task.beginAttempt();
        return task;
    }
}
