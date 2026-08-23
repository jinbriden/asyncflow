package io.github.asyncflow.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "asyncflow.outbox",
        name = {"enabled", "scheduler-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class OutboxScheduler {
    private final OutboxPublisher publisher;

    public OutboxScheduler(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${asyncflow.outbox.poll-interval:500}")
    public void publishPending() {
        publisher.publishPending();
    }
}
