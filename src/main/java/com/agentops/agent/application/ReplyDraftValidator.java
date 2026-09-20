package com.agentops.agent.application;

import com.agentops.knowledge.application.KnowledgeSearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ReplyDraftValidator {
    private final ObjectMapper mapper;
    public ReplyDraftValidator(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public ReplyDraft validate(String json, List<KnowledgeSearchHit> retrieved) {
        try {
            if (json == null || json.length() > 16_000) throw new IllegalArgumentException();
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject() || root.size() != 3
                    || !root.has("content") || !root.has("citationChunkIds") || !root.has("confidence"))
                throw new IllegalArgumentException();
            JsonNode body = root.get("content");
            JsonNode citations = root.get("citationChunkIds");
            JsonNode confidence = root.get("confidence");
            if (!body.isTextual() || !citations.isArray() || !confidence.isNumber()
                    || !Double.isFinite(confidence.asDouble()) || confidence.asDouble() < 0
                    || confidence.asDouble() > 1) throw new IllegalArgumentException();
            String content = body.asText().strip();
            if (content.isEmpty() || content.codePointCount(0, content.length()) > 2000
                    || citations.isEmpty()) throw new IllegalArgumentException();
            Set<UUID> allowed = new HashSet<>();
            retrieved.forEach(hit -> allowed.add(hit.chunkId()));
            Set<UUID> seen = new HashSet<>();
            java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
            for (JsonNode node : citations) {
                if (!node.isTextual()) throw new IllegalArgumentException();
                UUID id = UUID.fromString(node.asText());
                if (!allowed.contains(id) || !seen.add(id)
                        || !content.contains("[citation:" + id + "]")) throw new IllegalArgumentException();
                ids.add(id);
            }
            // Every citation marker in the answer must be declared and backed by a retrieved chunk.
            java.util.regex.Matcher markers = java.util.regex.Pattern.compile("\\[citation:([^\\]]+)\\]").matcher(content);
            while (markers.find()) if (!seen.contains(UUID.fromString(markers.group(1))))
                throw new IllegalArgumentException();
            return new ReplyDraft(content, List.copyOf(ids), confidence.asDouble());
        } catch (Exception exception) {
            throw new InvalidAnalysisOutputException(exception);
        }
    }
}
