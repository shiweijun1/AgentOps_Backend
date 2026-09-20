package com.agentops.agent.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentWorkerScheduler {
    private final AgentWorker worker;
    public AgentWorkerScheduler(AgentWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${agentops.agent.poll-interval:2s}")
    public void poll() {
        if (enabled) worker.runBatch();
    }

    @org.springframework.beans.factory.annotation.Value("${agentops.agent.worker-enabled:true}")
    private boolean enabled;
}
