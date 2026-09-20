package com.agentops.knowledge.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeChunkerTest {
    private final KnowledgeChunker chunker = new KnowledgeChunker();

    @Test void splitUsesCodePointsAndPreservesEmoji() {
        String content = "中".repeat(499) + "😀" + "文".repeat(20);
        var parts = chunker.split(content);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).index()).isZero();
        assertThat(parts.get(0).content().codePointCount(0, parts.get(0).content().length())).isEqualTo(500);
        assertThat(parts.get(0).content()).endsWith("😀");
        assertThat(parts.get(1).content()).contains("😀");
        assertThat(parts.get(1).index()).isEqualTo(1);
    }

    @Test void estimatedTokensAreExplicitlyApproximate() {
        assertThat(chunker.estimateTokens("中文abcd")).isEqualTo(3);
        assertThat(chunker.split("中文abcd").getFirst().estimatedTokenCount()).isEqualTo(3);
    }

    @Test void articleSizeIsLimited() {
        assertThatThrownBy(() -> chunker.split("文".repeat(20_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
