package io.github.asyncflow.service;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.report.SalesReportGenerator;
import io.github.asyncflow.repository.TaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskProcessor {
    private final TaskRepository tasks;
    private final RabbitTemplate rabbit;
    private final MeterRegistry metrics;
    private final TaskEventRecorder eventRecorder;
    private final DownstreamGateway downstream;
    private final SalesReportGenerator reportGenerator;

    public TaskProcessor(TaskRepository tasks, RabbitTemplate rabbit, MeterRegistry metrics,
                         TaskEventRecorder eventRecorder, DownstreamGateway downstream,
                         SalesReportGenerator reportGenerator) {
        this.tasks = tasks;
        this.rabbit = rabbit;
        this.metrics = metrics;
        this.eventRecorder = eventRecorder;
        this.downstream = downstream;
        this.reportGenerator = reportGenerator;
    }

    @Transactional
    public void process(TaskMessage message) {
        TaskRecord task = tasks.findById(message.taskId())
                .orElseThrow(() -> new TaskNotFoundException(message.taskId()));

        if (task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.CANCELLED
                || task.getStatus() == TaskStatus.DEAD || task.getStatus() == TaskStatus.COMPENSATED) {
            metrics.counter("asyncflow.messages.duplicate", "status", task.getStatus().name()).increment();
            return;
        }

        TaskStatus previous = task.getStatus();
        int attempt = task.beginAttempt();
        eventRecorder.record(task, previous, "WORKER", "Attempt " + attempt + " started");
        try {
            executeBusinessHandler(task);
            task.markSucceeded();
            eventRecorder.record(task, TaskStatus.RUNNING, "WORKER", "Task completed");
            metrics.counter("asyncflow.tasks.processed", "result", "success").increment();
        } catch (RuntimeException ex) {
            if (task.canRetry()) {
                task.markRetry(ex.getMessage());
                eventRecorder.record(task, TaskStatus.RUNNING, "WORKER", ex.getMessage());
                rabbit.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.RETRY_KEY,
                        new TaskMessage(task.getTaskId()));
                metrics.counter("asyncflow.tasks.processed", "result", "retry").increment();
            } else {
                task.markDeadLettered(ex.getMessage());
                eventRecorder.record(task, TaskStatus.RUNNING, "WORKER", ex.getMessage());
                rabbit.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.DEAD_KEY,
                        new TaskMessage(task.getTaskId()));
                metrics.counter("asyncflow.tasks.processed", "result", "dead_letter").increment();
            }
        }
        metrics.counter("asyncflow.task.attempts", "task_type", task.getTaskType()).increment();
    }

    private void executeBusinessHandler(TaskRecord task) {
        if (task.shouldSimulateFailure()) {
            throw new IllegalStateException("Injected failure on attempt " + task.getAttemptCount());
        }
        if (task.getPayload().contains("\"forcePermanentFailure\":true")) {
            throw new IllegalStateException("Permanent business failure");
        }
        if (task.getPayload().isBlank()) {
            throw new IllegalArgumentException("Task payload is empty");
        }
        if ("REPORT".equalsIgnoreCase(task.getTaskType())) {
            reportGenerator.generate(task);
        } else {
            downstream.execute(task);
        }
    }
}
