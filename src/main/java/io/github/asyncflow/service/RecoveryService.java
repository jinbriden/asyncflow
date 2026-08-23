package io.github.asyncflow.service;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.TaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryService {
    private final TaskRepository tasks;
    private final RabbitTemplate rabbit;
    private final MeterRegistry metrics;
    private final TaskEventRecorder eventRecorder;

    public RecoveryService(TaskRepository tasks, RabbitTemplate rabbit, MeterRegistry metrics,
                           TaskEventRecorder eventRecorder) {
        this.tasks = tasks;
        this.rabbit = rabbit;
        this.metrics = metrics;
        this.eventRecorder = eventRecorder;
    }

    @Transactional
    public TaskRecord replay(String taskId) {
        TaskRecord task = find(taskId);
        if (task.getStatus() != TaskStatus.DEAD) {
            throw new IllegalStateException("Only dead-lettered tasks can be replayed");
        }
        task.queue();
        eventRecorder.record(task, TaskStatus.DEAD, "ADMIN", "Manual replay");
        rabbit.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.TASK_KEY, new TaskMessage(taskId));
        metrics.counter("asyncflow.tasks.recovery", "action", "replay").increment();
        return task;
    }

    @Transactional
    public TaskRecord compensate(String taskId) {
        TaskRecord task = find(taskId);
        if (task.getStatus() != TaskStatus.DEAD) {
            throw new IllegalStateException("Only dead-lettered tasks can be compensated");
        }
        task.startCompensation();
        eventRecorder.record(task, TaskStatus.DEAD, "ADMIN", "Compensation started");
        // The demo compensation handler is deterministic. Replace this boundary with a domain-specific rollback.
        task.markCompensated();
        eventRecorder.record(task, TaskStatus.COMPENSATING, "ADMIN", "Compensation completed");
        metrics.counter("asyncflow.tasks.recovery", "action", "compensate").increment();
        return task;
    }

    private TaskRecord find(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}
