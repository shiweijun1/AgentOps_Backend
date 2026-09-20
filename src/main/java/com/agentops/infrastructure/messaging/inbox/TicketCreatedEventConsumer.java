package com.agentops.infrastructure.messaging.inbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import com.agentops.ticket.event.TicketCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "agentops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TicketCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketCreatedEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InboxProcessingService processingService;
    private final MessagingProperties properties;

    public TicketCreatedEventConsumer(ObjectMapper objectMapper, InboxProcessingService processingService,
                                      MessagingProperties properties) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.properties = properties;
    }

    @RabbitListener(queues = "${agentops.messaging.ticket-created-queue:agentops.ticket-created}")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        TicketCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, TicketCreatedEvent.class);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Rejecting malformed event: eventId={}, eventType={}, aggregateId={}, traceId={}",
                    header(message, "eventId"), header(message, "eventType"),
                    header(message, "aggregateId"), header(message, "traceId")
            );
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            InboxProcessingService.ProcessingResult result = processingService.process(event, payload);
            channel.basicAck(deliveryTag, false);
            log.info(
                    "Event consumed: eventId={}, eventType={}, aggregateId={}, traceId={}, result={}",
                    event.eventId(), event.eventType(), event.aggregateId(), event.traceId(), result
            );
        } catch (Exception exception) {
            int retryCount;
            try {
                retryCount = processingService.recordFailure(event, payload, exception);
            } catch (Exception failureRecordingException) {
                log.error(
                        "Inbox failure recording failed: eventId={}, eventType={}, aggregateId={}, traceId={}, errorType={}",
                        event.eventId(), event.eventType(), event.aggregateId(), event.traceId(),
                        failureRecordingException.getClass().getSimpleName()
                );
                channel.basicNack(deliveryTag, false, true);
                return;
            }

            boolean retryable = !(exception instanceof NonRetryableEventProcessingException);
            boolean retry = retryable && retryCount < properties.getConsumer().getMaxRetries();
            log.warn(
                    "Event consumption failed: eventId={}, eventType={}, aggregateId={}, traceId={}, retryCount={}, requeue={}, errorType={}",
                    event.eventId(), event.eventType(), event.aggregateId(), event.traceId(), retryCount, retry,
                    exception.getClass().getSimpleName()
            );
            if (retry) {
                channel.basicNack(deliveryTag, false, true);
            } else {
                channel.basicReject(deliveryTag, false);
            }
        }
    }

    private Object header(Message message, String name) {
        return message.getMessageProperties().getHeaders().get(name);
    }
}
