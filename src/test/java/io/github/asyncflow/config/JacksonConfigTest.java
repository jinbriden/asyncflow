package io.github.asyncflow.config;

import io.github.asyncflow.messaging.TaskMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void shouldRoundTripProjectMessageType() {
        Jackson2JsonMessageConverter converter = new JacksonConfig().rabbitMessageConverter();
        TaskMessage original = new TaskMessage("task-123");

        Message message = converter.toMessage(original, new MessageProperties());
        Object restored = converter.fromMessage(message);

        assertThat(restored).isEqualTo(original);
    }
}
