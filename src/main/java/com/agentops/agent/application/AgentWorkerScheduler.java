package com.agentops.agent.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentWorkerScheduler {
    private final AgentWorker worker;
    private final ReplySuggestionWorker replyWorker;
    public AgentWorkerScheduler(AgentWorker worker, ReplySuggestionWorker replyWorker) {
        this.worker = worker; this.replyWorker = replyWorker;
    }

    @Scheduled(fixedDelayString = "${agentops.agent.poll-interval:2s}")
    public void poll() {
        if (enabled) { worker.runBatch(); replyWorker.runBatch(); }
    }

    @org.springframework.beans.factory.annotation.Value("${agentops.agent.worker-enabled:true}")
    private boolean enabled;
}
