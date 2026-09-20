package com.agentops.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "agentops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMessagingConfiguration {

    @Bean
    TopicExchange agentOpsExchange(MessagingProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    DirectExchange agentOpsDeadLetterExchange(MessagingProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    Queue ticketCreatedQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getTicketCreatedQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getDeadLetterRoutingKey())
                .build();
    }

    @Bean
    Binding ticketCreatedBinding(Queue ticketCreatedQueue, TopicExchange agentOpsExchange,
                                 MessagingProperties properties) {
        return BindingBuilder.bind(ticketCreatedQueue)
                .to(agentOpsExchange)
                .with(properties.getTicketCreatedRoutingKey());
    }

    @Bean
    Queue ticketCreatedDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    Binding ticketCreatedDeadLetterBinding(
            Queue ticketCreatedDeadLetterQueue,
            DirectExchange agentOpsDeadLetterExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder.bind(ticketCreatedDeadLetterQueue)
                .to(agentOpsDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }
}
