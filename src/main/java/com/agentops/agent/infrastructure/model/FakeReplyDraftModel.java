package com.agentops.agent.infrastructure.model;

import com.agentops.agent.application.ReplyDraftModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agentops.ai", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeReplyDraftModel implements ReplyDraftModel {
    private final ObjectMapper mapper;
    public FakeReplyDraftModel(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public ModelResponse draft(ModelRequest input) throws Exception {
        var source = input.sources().getFirst();
        return new ModelResponse(mapper.writeValueAsString(Map.of(
                "content", "请参考知识库资料处理此问题。[citation:" + source.chunkId() + "]",
                "citationChunkIds", new String[]{source.chunkId().toString()}, "confidence", 0.8)),
                "fake-reply-v1", 0, 0);
    }
    @Override public String provider() { return "fake"; }
}
