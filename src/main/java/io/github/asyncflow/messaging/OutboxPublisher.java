package io.github.asyncflow.messaging;

import io.github.asyncflow.config.RabbitTopology;
import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.service.TaskEventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "asyncflow.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository events;
    private final TaskRepository tasks;
    private final RabbitTemplate rabbit;
    private final TaskEventRecorder eventRecorder;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final long confirmTimeoutMillis;

    public OutboxPublisher(OutboxEventRepository events, TaskRepository tasks, RabbitTemplate rabbit,
                           TaskEventRecorder eventRecorder,
                           PlatformTransactionManager transactionManager,
                           @Value("${asyncflow.outbox.batch-size:50}") int batchSize,
                           @Value("${asyncflow.outbox.confirm-timeout:2000}") long confirmTimeoutMillis) {
        this.events = events;
        this.tasks = tasks;
        this.rabbit = rabbit;
        this.eventRecorder = eventRecorder;
        this.transactions = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.confirmTimeoutMillis = confirmTimeoutMillis;
    }

    public void publishPending() {
        List<OutboxEvent> pending = events.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            try {
                queueTaskBeforePublishing(event);
                CorrelationData correlation = new CorrelationData(event.getEventId());
                rabbit.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.TASK_KEY,
                        new TaskMessage(event.getAggregateId()), message -> {
                            message.getMessageProperties().setMessageId(event.getEventId());
                            message.getMessageProperties().setHeader("x-aggregate-id", event.getAggregateId());
                            return message;
                        }, correlation);
                awaitBrokerConfirm(event, correlation);
                markPublished(event.getEventId());
            } catch (RuntimeException ex) {
                markPublishFailed(event.getEventId());
                log.warn("Outbox publish failed for event {}: {}", event.getEventId(), ex.getMessage());
            }
        }
    }

    private void queueTaskBeforePublishing(OutboxEvent event) {
        transactions.executeWithoutResult(status -> {
            TaskRecord task = tasks.findById(event.getAggregateId()).orElse(null);
            if (task != null && task.getStatus() == TaskStatus.CREATED) {
                task.queue();
                eventRecorder.record(task, TaskStatus.CREATED, "OUTBOX", "Ready for message publish");
            }
        });
    }

    private void markPublished(String eventId) {
        transactions.executeWithoutResult(status -> events.findById(eventId)
                .ifPresent(OutboxEvent::markPublished));
    }

    private void markPublishFailed(String eventId) {
        transactions.executeWithoutResult(status -> events.findById(eventId)
                .ifPresent(OutboxEvent::markPublishFailed));
    }

    private void awaitBrokerConfirm(OutboxEvent event, CorrelationData correlation) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("Broker negatively acknowledged event " + event.getEventId()
                        + ": " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new AmqpException("Broker returned unroutable event " + event.getEventId());
            }
        } catch (TimeoutException ex) {
            throw new AmqpException("Timed out waiting for broker confirm for event " + event.getEventId(), ex);
        } catch (ExecutionException ex) {
            throw new AmqpException("Broker confirm failed for event " + event.getEventId(), ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for broker confirm for event " + event.getEventId(), ex);
        }
    }
}
