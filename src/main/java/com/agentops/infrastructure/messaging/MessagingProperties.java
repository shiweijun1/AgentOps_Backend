package com.agentops.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agentops.messaging")
public class MessagingProperties {

    private boolean enabled = true;
    private String exchange = "agentops.events";
    private String ticketCreatedQueue = "agentops.ticket-created";
    private String ticketCreatedRoutingKey = "ticket.created.v1";
    private String deadLetterExchange = "agentops.dlx";
    private String deadLetterQueue = "agentops.ticket-created.dlq";
    private String deadLetterRoutingKey = "ticket.created.dead";
    private String consumerName = "ticket-created-consumer-v1";
    private final Outbox outbox = new Outbox();
    private final Consumer consumer = new Consumer();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getTicketCreatedQueue() { return ticketCreatedQueue; }
    public void setTicketCreatedQueue(String value) { this.ticketCreatedQueue = value; }
    public String getTicketCreatedRoutingKey() { return ticketCreatedRoutingKey; }
    public void setTicketCreatedRoutingKey(String value) { this.ticketCreatedRoutingKey = value; }
    public String getDeadLetterExchange() { return deadLetterExchange; }
    public void setDeadLetterExchange(String value) { this.deadLetterExchange = value; }
    public String getDeadLetterQueue() { return deadLetterQueue; }
    public void setDeadLetterQueue(String value) { this.deadLetterQueue = value; }
    public String getDeadLetterRoutingKey() { return deadLetterRoutingKey; }
    public void setDeadLetterRoutingKey(String value) { this.deadLetterRoutingKey = value; }
    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String value) { this.consumerName = value; }
    public Outbox getOutbox() { return outbox; }
    public Consumer getConsumer() { return consumer; }

    public static class Outbox {
        private boolean schedulingEnabled = true;
        private int batchSize = 20;
        private int maxRetries = 5;
        private Duration publishInterval = Duration.ofSeconds(1);
        private Duration leaseDuration = Duration.ofSeconds(30);
        private Duration confirmTimeout = Duration.ofSeconds(5);
        private Duration retryBaseDelay = Duration.ofSeconds(5);
        private Duration retryMaxDelay = Duration.ofMinutes(5);

        public boolean isSchedulingEnabled() { return schedulingEnabled; }
        public void setSchedulingEnabled(boolean value) { this.schedulingEnabled = value; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int value) { this.batchSize = value; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int value) { this.maxRetries = value; }
        public Duration getPublishInterval() { return publishInterval; }
        public void setPublishInterval(Duration value) { this.publishInterval = value; }
        public Duration getLeaseDuration() { return leaseDuration; }
        public void setLeaseDuration(Duration value) { this.leaseDuration = value; }
        public Duration getConfirmTimeout() { return confirmTimeout; }
        public void setConfirmTimeout(Duration value) { this.confirmTimeout = value; }
        public Duration getRetryBaseDelay() { return retryBaseDelay; }
        public void setRetryBaseDelay(Duration value) { this.retryBaseDelay = value; }
        public Duration getRetryMaxDelay() { return retryMaxDelay; }
        public void setRetryMaxDelay(Duration value) { this.retryMaxDelay = value; }
    }

    public static class Consumer {
        private int maxRetries = 3;

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int value) { this.maxRetries = value; }
    }
}
