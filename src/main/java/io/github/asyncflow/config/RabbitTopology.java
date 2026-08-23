package io.github.asyncflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {
    public static final String TASK_EXCHANGE = "asyncflow.task.exchange";
    public static final String TASK_QUEUE = "asyncflow.task.queue";
    public static final String RETRY_QUEUE = "asyncflow.task.retry.queue";
    public static final String DEAD_LETTER_QUEUE = "asyncflow.task.dlq";
    public static final String TASK_KEY = "task.execute";
    public static final String RETRY_KEY = "task.retry";
    public static final String DEAD_KEY = "task.dead";

    @Bean
    DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    @Bean
    Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
                .deadLetterExchange(TASK_EXCHANGE)
                .deadLetterRoutingKey(DEAD_KEY)
                .build();
    }

    @Bean
    Queue retryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(5_000)
                .deadLetterExchange(TASK_EXCHANGE)
                .deadLetterRoutingKey(TASK_KEY)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding taskBinding(Queue taskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(taskQueue).to(taskExchange).with(TASK_KEY);
    }

    @Bean
    Binding retryBinding(Queue retryQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(retryQueue).to(taskExchange).with(RETRY_KEY);
    }

    @Bean
    Binding deadBinding(Queue deadLetterQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(taskExchange).with(DEAD_KEY);
    }
}
