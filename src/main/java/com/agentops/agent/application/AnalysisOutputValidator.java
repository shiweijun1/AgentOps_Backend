package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.agentops.agent.domain.RiskLevel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class AnalysisOutputValidator {
    private final ObjectMapper strictMapper;
    private final AgentProperties properties;

    public AnalysisOutputValidator(ObjectMapper mapper, AgentProperties properties) {
        this.strictMapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.properties = properties;
    }

    public AnalysisOutput validate(String json) {
        try {
            JsonNode root = strictMapper.readTree(json);
            Set<String> fields = Set.of("categoryCode", "priority", "sentiment", "riskLevel",
                    "confidence", "manualRequired", "manualReason", "reason");
            if (root == null || !root.isObject() || root.size() != fields.size()
                    || !fields.stream().allMatch(root::has)
                    || !root.path("categoryCode").isTextual() || !root.path("priority").isTextual()
                    || !root.path("sentiment").isTextual() || !root.path("riskLevel").isTextual()
                    || !root.path("confidence").isNumber() || !root.path("manualRequired").isBoolean()
                    || !root.path("reason").isTextual()
                    || !(root.path("manualReason").isNull() || root.path("manualReason").isTextual())) {
                throw new IllegalArgumentException("Invalid analysis JSON structure");
            }
            AnalysisOutput value = strictMapper.readValue(json, AnalysisOutput.class);
            if (value.categoryCode() == null || value.priority() == null || value.sentiment() == null
                    || value.riskLevel() == null || value.confidence() == null
                    || !Double.isFinite(value.confidence()) || value.manualRequired() == null
                    || value.confidence() < 0 || value.confidence() > 1
                    || value.reason() == null || value.reason().isBlank() || value.reason().length() > 1000
                    || value.manualReason() != null && value.manualReason().length() > 64) {
                throw new IllegalArgumentException("Invalid analysis fields");
            }
            boolean forced = value.riskLevel() == RiskLevel.HIGH
                    || value.confidence() < properties.getLowConfidenceThreshold();
            if (forced) {
                String reason = value.riskLevel() == RiskLevel.HIGH ? "HIGH_RISK" : "LOW_CONFIDENCE";
                return new AnalysisOutput(value.categoryCode(), value.priority(), value.sentiment(),
                        value.riskLevel(), value.confidence(), true, reason, value.reason());
            }
            if (value.manualRequired() && (value.manualReason() == null || value.manualReason().isBlank())) {
                throw new IllegalArgumentException("manualReason required");
            }
            return value;
        } catch (Exception exception) {
            throw new InvalidAnalysisOutputException(exception);
        }
    }
}
