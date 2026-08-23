package io.github.asyncflow.service;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.report.SalesReportGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskProcessorTest {
    private TaskRepository repository;
    private RabbitTemplate rabbit;
    private TaskEventRecorder recorder;
    private TaskProcessor processor;
    private DownstreamGateway downstream;
    private SalesReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        repository = mock(TaskRepository.class);
        rabbit = mock(RabbitTemplate.class);
        recorder = mock(TaskEventRecorder.class);
        downstream = mock(DownstreamGateway.class);
        reportGenerator = mock(SalesReportGenerator.class);
        processor = new TaskProcessor(repository, rabbit, new SimpleMeterRegistry(), recorder, downstream, reportGenerator);
    }

    @Test
    void completesHealthyTask() {
        TaskRecord task = queuedTask(3, 0, "{\"runId\":\"ok\"}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        verify(reportGenerator).generate(task);
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void routesTemporaryFailureToRetryQueue() {
        TaskRecord task = queuedTask(3, 1, "{\"runId\":\"retry\"}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RETRYING);
        verify(rabbit).convertAndSend(eq(RabbitTopology.TASK_EXCHANGE), eq(RabbitTopology.RETRY_KEY), any(TaskMessage.class));
    }

    @Test
    void succeedsOnThirdAttemptAfterTwoTimeouts() {
        TaskRecord task = queuedTask(3, 2, "{\"runId\":\"recover\"}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        processor.process(new TaskMessage(task.getTaskId()));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void routesExhaustedFailureToDeadLetterQueue() {
        TaskRecord task = queuedTask(1, 1, "{\"runId\":\"dead\"}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DEAD);
        verify(rabbit).convertAndSend(eq(RabbitTopology.TASK_EXCHANGE), eq(RabbitTopology.DEAD_KEY), any(TaskMessage.class));
    }

    @Test
    void permanentFailureNeverCreatesBusinessResult() {
        TaskRecord task = queuedTask(2, 0, "{\"forcePermanentFailure\":true}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getFailureReason()).contains("Permanent business failure");
    }

    @Test
    void duplicateMessageAfterSuccessIsIdempotent() {
        TaskRecord task = queuedTask(3, 0, "{\"runId\":\"duplicate\"}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void duplicateMessageAfterCancellationIsIgnored() {
        TaskRecord task = TaskRecord.create("cancel-key", "REPORT", "{}", 3, 0);
        task.cancel();
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        assertThat(task.getAttemptCount()).isZero();
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void missingTaskProducesExplicitDiagnostic() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> processor.process(new TaskMessage("missing")))
                .isInstanceOf(TaskNotFoundException.class).hasMessageContaining("missing");
    }

    @Test
    void eventRecorderCapturesRunningAndTerminalStates() {
        TaskRecord task = queuedTask(3, 0, "{\"runId\":\"events\"}");
        when(repository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        processor.process(new TaskMessage(task.getTaskId()));
        ArgumentCaptor<TaskStatus> previous = ArgumentCaptor.forClass(TaskStatus.class);
        verify(recorder, org.mockito.Mockito.times(2)).record(eq(task), previous.capture(), eq("WORKER"), any(String.class));
        assertThat(previous.getAllValues()).containsExactly(TaskStatus.QUEUED, TaskStatus.RUNNING);
    }

    private TaskRecord queuedTask(int maxAttempts, int failures, String payload) {
        TaskRecord task = TaskRecord.create("key-" + System.nanoTime(), "REPORT", payload, maxAttempts, failures);
        task.queue();
        return task;
    }
}
