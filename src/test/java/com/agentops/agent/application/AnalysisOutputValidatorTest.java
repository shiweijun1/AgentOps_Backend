package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisOutputValidatorTest {
    private final AnalysisOutputValidator validator = new AnalysisOutputValidator(
            new ObjectMapper(), new AgentProperties());

    @Test void lowConfidenceForcesManualReview() {
        AnalysisOutput output = validator.validate(json("LOW", 0.4));
        assertThat(output.manualRequired()).isTrue();
        assertThat(output.manualReason()).isEqualTo("LOW_CONFIDENCE");
    }

    @Test void outOfRangeConfidenceIsRejected() {
        assertThatThrownBy(() -> validator.validate(json("LOW", 1.2)))
                .isInstanceOf(InvalidAnalysisOutputException.class);
    }

    @Test void unknownOrMissingFieldsAreRejected() {
        assertThatThrownBy(() -> validator.validate(json("LOW", 0.8).replace("\"reason\"", "\"unknown\"")))
                .isInstanceOf(InvalidAnalysisOutputException.class);
        assertThatThrownBy(() -> validator.validate("{}"))
                .isInstanceOf(InvalidAnalysisOutputException.class);
    }

    @Test void stringConfidenceAndDuplicateKeysAreRejected() {
        assertThatThrownBy(() -> validator.validate(json("LOW", 0.8)
                .replace("\"confidence\":0.8", "\"confidence\":\"0.8\"")))
                .isInstanceOf(InvalidAnalysisOutputException.class);
        assertThatThrownBy(() -> validator.validate(json("LOW", 0.8)
                .replace("\"reason\":\"test\"", "\"reason\":\"test\",\"reason\":\"again\"")))
                .isInstanceOf(InvalidAnalysisOutputException.class);
    }

    private String json(String risk, double confidence) {
        return """
                {"categoryCode":"OTHER","priority":"LOW","sentiment":"NEUTRAL",
                 "riskLevel":"%s","confidence":%s,"manualRequired":false,
                 "manualReason":null,"reason":"test"}
                """.formatted(risk, confidence);
    }
}
