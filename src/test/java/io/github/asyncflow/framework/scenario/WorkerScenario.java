package io.github.asyncflow.framework.scenario;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.messaging.TaskMessage;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.service.TaskProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkerScenario {
    private final TaskRepository tasks;
    private final TaskProcessor processor;

    public WorkerScenario(TaskRepository tasks, TaskProcessor processor) {
        this.tasks = tasks;
        this.processor = processor;
    }

    public void queueAndProcess(String taskId) {
        TaskRecord task = tasks.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        task.queue();
        tasks.saveAndFlush(task);
        processor.process(new TaskMessage(taskId));
    }

    public void process(String taskId) {
        processor.process(new TaskMessage(taskId));
    }
}
