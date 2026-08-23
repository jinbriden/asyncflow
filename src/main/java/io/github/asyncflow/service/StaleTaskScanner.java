package io.github.asyncflow.service;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.TaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "asyncflow.scanner.enabled", havingValue = "true", matchIfMissing = true)
public class StaleTaskScanner {
    private final TaskRepository tasks;
    private final TaskEventRecorder eventRecorder;
    private final RabbitTemplate rabbit;
    private final MeterRegistry metrics;
    private final Duration staleAfter;

    public StaleTaskScanner(TaskRepository tasks, TaskEventRecorder eventRecorder, RabbitTemplate rabbit,
                            MeterRegistry metrics,
                            @Value("${asyncflow.scanner.stale-after:PT2M}") Duration staleAfter) {
        this.tasks = tasks;
        this.eventRecorder = eventRecorder;
        this.rabbit = rabbit;
        this.metrics = metrics;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${asyncflow.scanner.interval:30000}")
    @Transactional
    public void recoverStaleRunningTasks() {
        List<TaskRecord> stale = tasks.findByStatusAndUpdatedAtBefore(TaskStatus.RUNNING,
                Instant.now().minus(staleAfter), PageRequest.of(0, 100));
        for (TaskRecord task : stale) {
            if (task.canRetry()) {
                task.markRetry("Recovered stale RUNNING task");
                eventRecorder.record(task, TaskStatus.RUNNING, "SCANNER", "Worker heartbeat timed out");
                rabbit.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.RETRY_KEY,
                        new TaskMessage(task.getTaskId()));
            } else {
                task.markDeadLettered("Stale task exceeded max attempts");
                eventRecorder.record(task, TaskStatus.RUNNING, "SCANNER", "Retry budget exhausted");
                rabbit.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.DEAD_KEY,
                        new TaskMessage(task.getTaskId()));
            }
            metrics.counter("asyncflow.tasks.stale.recovered", "result", task.getStatus().name()).increment();
        }
    }
}
