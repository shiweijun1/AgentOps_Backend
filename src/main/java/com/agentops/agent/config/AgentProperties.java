package com.agentops.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "agentops.agent")
public class AgentProperties {
    private boolean workerEnabled = true;
    private int batchSize = 10;
    private Duration pollInterval = Duration.ofSeconds(2);
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration modelTimeout = Duration.ofSeconds(30);
    private double lowConfidenceThreshold = 0.7;

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean value) { workerEnabled = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration value) { pollInterval = value; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration value) { leaseDuration = value; }
    public Duration getModelTimeout() { return modelTimeout; }
    public void setModelTimeout(Duration value) { modelTimeout = value; }
    public double getLowConfidenceThreshold() { return lowConfidenceThreshold; }
    public void setLowConfidenceThreshold(double value) { lowConfidenceThreshold = value; }
}
