package com.agentops.knowledge.application;

import com.agentops.knowledge.domain.KnowledgeChunkingStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeChunkerTest {
    private final KnowledgeChunker chunker = new KnowledgeChunker();

    @Test void headingsBecomeSectionPathButNotCitationContent() {
        String source = "# 账号指南\n\n## 双重认证\n\n更换手机后需要重新绑定😀。";
        var part = chunker.split("企业帮助中心", source).getFirst();

        assertThat(part.index()).isZero();
        assertThat(part.sectionPath()).isEqualTo("账号指南 > 双重认证");
        assertThat(part.content()).isEqualTo("更换手机后需要重新绑定😀。");
        assertThat(part.searchText()).isEqualTo("企业帮助中心\n账号指南 > 双重认证\n更换手机后需要重新绑定😀。");
        assertThat(part.strategy()).isEqualTo(KnowledgeChunkingStrategy.SEMANTIC_BOUNDARY_V2);
        assertThat(part.chunkHash()).hasSize(64);
        assertThat(source.codePoints().skip(part.startOffset())
                .limit(part.endOffset() - part.startOffset()).collect(StringBuilder::new,
                        StringBuilder::appendCodePoint, StringBuilder::append).toString())
                .isEqualTo(part.content());
    }

    @Test void recursivelyPrefersParagraphThenSentenceThenPunctuationBoundaries() {
        String paragraphs = "甲".repeat(300) + "\n\n" + "乙".repeat(300);
        assertThat(chunker.split("标题", paragraphs)).extracting(KnowledgeChunker.Part::content)
                .containsExactly("甲".repeat(300), "乙".repeat(300));

        String sentences = "甲".repeat(350) + "。" + "乙".repeat(350) + "。";
        assertThat(chunker.split("标题", sentences)).extracting(KnowledgeChunker.Part::content)
                .containsExactly("甲".repeat(350) + "。", "乙".repeat(350) + "。");

        String punctuation = "甲".repeat(350) + "，" + "乙".repeat(350);
        assertThat(chunker.split("标题", punctuation)).extracting(KnowledgeChunker.Part::content)
                .containsExactly("甲".repeat(350) + "，", "乙".repeat(350));
    }

    @Test void unicodeHardCutNeverBreaksSurrogatePair() {
        String source = "中".repeat(599) + "😀" + "文".repeat(10);
        var parts = chunker.split("标题", source);
        assertThat(parts).hasSize(2);
        assertThat(parts).allSatisfy(part -> {
            assertThat(part.content()).doesNotContain("�");
            assertThat(part.estimatedTokenCount()).isLessThanOrEqualTo(KnowledgeChunker.MAX_CHUNK_TOKENS);
        });
        assertThat(parts.stream().map(KnowledgeChunker.Part::content).reduce("", String::concat)).isEqualTo(source);
    }

    @Test void estimatedTokensRemainExplicitlyApproximateAndInputIsLimited() {
        assertThat(chunker.estimateTokens("中文abcd")).isEqualTo(3);
        assertThat(chunker.split("标题", "中文abcd").getFirst().estimatedTokenCount()).isEqualTo(3);
        assertThatThrownBy(() -> chunker.split("标题", "文".repeat(20_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chunker.split("标题", "# 只有标题"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
