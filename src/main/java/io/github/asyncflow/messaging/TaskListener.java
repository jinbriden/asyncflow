package io.github.asyncflow.messaging;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.service.TaskProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TaskListener {
    private final TaskProcessor processor;

    public TaskListener(TaskProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitTopology.TASK_QUEUE, concurrency = "${asyncflow.consumer.concurrency:2}")
    public void consume(TaskMessage message) {
        processor.process(message);
    }
}
