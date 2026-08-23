package io.github.asyncflow.service;

import io.github.asyncflow.domain.TaskEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.repository.TaskEventRepository;
import org.springframework.stereotype.Component;

@Component
public class TaskEventRecorder {
    private final TaskEventRepository events;

    public TaskEventRecorder(TaskEventRepository events) {
        this.events = events;
    }

    public void record(TaskRecord task, TaskStatus from, String source, String reason) {
        events.save(TaskEvent.of(task.getTaskId(), from, task.getStatus(), source, reason));
    }
}
