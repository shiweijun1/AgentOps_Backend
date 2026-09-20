package com.agentops.infrastructure.messaging.outbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "agentops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitEventBroker implements EventBroker {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public RabbitEventBroker(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.rabbitTemplate.setMandatory(true);
    }

    @Override
    public void publish(OutboxDispatch event) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setMessageId(event.eventId());
        messageProperties.setHeader("eventId", event.eventId());
        messageProperties.setHeader("eventType", event.eventType());
        messageProperties.setHeader("aggregateId", event.aggregateId());
        if (event.traceId() != null) {
            messageProperties.setHeader("traceId", event.traceId());
        }

        Message message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), messageProperties);
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.send(
                properties.getExchange(), properties.getTicketCreatedRoutingKey(), message, correlation
        );

        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.getOutbox().getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS
            );
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ publisher confirm was negative: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned the event as unroutable");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ confirm", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ publisher confirm failed", exception);
        }
    }
}
