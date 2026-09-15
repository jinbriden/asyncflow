package io.github.asyncflow.service;

import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCreationTransaction {
    private final TaskRepository tasks;
    private final OutboxEventRepository outbox;
    private final TaskEventRecorder eventRecorder;

    public TaskCreationTransaction(TaskRepository tasks, OutboxEventRepository outbox,
                                   TaskEventRecorder eventRecorder) {
        this.tasks = tasks;
        this.outbox = outbox;
        this.eventRecorder = eventRecorder;
    }

    @Transactional
    public void create(TaskRecord task, String payload) {
        tasks.saveAndFlush(task);
        eventRecorder.record(task, null, "API", "Task accepted");
        outbox.save(OutboxEvent.taskCreated(task.getTaskId(), payload));
    }
}
