package com.agentops.agent.application;

import java.util.List;
import java.util.UUID;

public record ReplyDraft(String content, List<UUID> citationChunkIds, double confidence) {}
