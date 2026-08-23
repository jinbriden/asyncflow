package io.github.asyncflow.service;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.repository.TaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCancellationService {
    private final TaskRepository tasks;
    private final TaskEventRecorder eventRecorder;
    private final MeterRegistry metrics;

    public TaskCancellationService(TaskRepository tasks, TaskEventRecorder eventRecorder, MeterRegistry metrics) {
        this.tasks = tasks;
        this.eventRecorder = eventRecorder;
        this.metrics = metrics;
    }

    @Transactional
    public TaskRecord cancel(String taskId) {
        TaskRecord task = tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        TaskStatus previous = task.getStatus();
        task.cancel();
        eventRecorder.record(task, previous, "API", "Task cancelled");
        metrics.counter("asyncflow.tasks.cancelled").increment();
        return task;
    }
}
