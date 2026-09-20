package com.agentops.agent.application;

import com.agentops.knowledge.application.KnowledgeSearchHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ReplyDraftValidatorTest {
    private final ReplyDraftValidator validator = new ReplyDraftValidator(new ObjectMapper());
    private final UUID chunk = UUID.randomUUID();
    private final List<KnowledgeSearchHit> hits = List.of(new KnowledgeSearchHit(
            UUID.randomUUID(), UUID.randomUUID(), chunk, "指南", "真实资料", 1.0));

    @Test void acceptsOnlyRetrievedCitationAndMarker() {
        var draft = validator.validate("{\"content\":\"参考资料 [citation:" + chunk +
                "]\",\"citationChunkIds\":[\"" + chunk + "\"],\"confidence\":0.8}", hits);
        assertThat(draft.citationChunkIds()).containsExactly(chunk);
    }

    @Test void rejectsInventedOrMissingCitation() {
        UUID invented = UUID.randomUUID();
        assertThatThrownBy(() -> validator.validate("{\"content\":\"[citation:" + invented +
                "]\",\"citationChunkIds\":[\"" + invented + "\"],\"confidence\":0.8}", hits))
                .isInstanceOf(InvalidAnalysisOutputException.class);
        assertThatThrownBy(() -> validator.validate("{\"content\":\"无引用\",\"citationChunkIds\":[\""
                + chunk + "\"],\"confidence\":0.8}", hits))
                .isInstanceOf(InvalidAnalysisOutputException.class);
    }

    @Test void rejectsInvalidConfidenceAndExtraFields() {
        assertThatThrownBy(() -> validator.validate("{\"content\":\"[citation:" + chunk +
                "]\",\"citationChunkIds\":[\"" + chunk + "\"],\"confidence\":1.1}", hits))
                .isInstanceOf(InvalidAnalysisOutputException.class);
        assertThatThrownBy(() -> validator.validate("{\"content\":\"[citation:" + chunk +
                "]\",\"citationChunkIds\":[\"" + chunk + "\"],\"confidence\":0.8,\"extra\":true}", hits))
                .isInstanceOf(InvalidAnalysisOutputException.class);
        assertThatThrownBy(() -> validator.validate("{\"content\":\"[citation:" + chunk +
                "]\",\"citationChunkIds\":[\"" + chunk + "\"],\"confidence\":0.8,\"confidence\":0.9}", hits))
                .isInstanceOf(InvalidAnalysisOutputException.class);
    }
}
