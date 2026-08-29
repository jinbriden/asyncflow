package io.github.asyncflow.framework.client;

import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.report.ReportResultRepository;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;

import java.util.List;
import java.util.Optional;

public class StoreClient {
    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final OutboxEventRepository outbox;
    private final ReportResultRepository reports;

    public StoreClient(TaskRepository tasks, TaskEventRepository events, OutboxEventRepository outbox,
                       ReportResultRepository reports) {
        this.tasks = tasks;
        this.events = events;
        this.outbox = outbox;
        this.reports = reports;
    }

    public StoreClient(TaskRepository tasks, TaskEventRepository events, OutboxEventRepository outbox) {
        this(tasks, events, outbox, null);
    }

    public long taskCount() {
        return tasks.count();
    }

    public long outboxCount() {
        return outbox.count();
    }

    public long reportResultCount() {
        return reports.count();
    }

    public Optional<TaskRecord> findByIdempotencyKey(String key) {
        return tasks.findByIdempotencyKey(key);
    }

    public TaskRecord requireTask(String taskId) {
        return tasks.findById(taskId).orElseThrow();
    }

    public TaskRecord saveTask(TaskRecord task) {
        return tasks.save(task);
    }

    public List<OutboxEvent> outboxEvents() {
        return outbox.findAll();
    }

    public TaskRepository tasks() {
        return tasks;
    }

    public TaskEventRepository events() {
        return events;
    }
}
