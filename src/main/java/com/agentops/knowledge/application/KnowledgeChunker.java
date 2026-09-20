package com.agentops.knowledge.application;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Fixed code-point windows; 50-code-point overlap protects terms at boundaries. */
@Component
public class KnowledgeChunker {
    public static final int MAX_ARTICLE_CODE_POINTS = 20_000;
    public static final int CHUNK_CODE_POINTS = 500;
    public static final int OVERLAP_CODE_POINTS = 50;
    public static final int MAX_CHUNKS = 50;

    public List<Part> split(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Knowledge content is empty");
        int total = content.codePointCount(0, content.length());
        if (total > MAX_ARTICLE_CODE_POINTS) throw new IllegalArgumentException("Knowledge article is too large");
        List<Part> parts = new ArrayList<>();
        for (int start = 0; start < total; start += CHUNK_CODE_POINTS - OVERLAP_CODE_POINTS) {
            int end = Math.min(start + CHUNK_CODE_POINTS, total);
            String chunk = content.substring(content.offsetByCodePoints(0, start),
                    content.offsetByCodePoints(0, end));
            parts.add(new Part(parts.size(), chunk, estimateTokens(chunk)));
            if (parts.size() > MAX_CHUNKS) throw new IllegalArgumentException("Too many knowledge chunks");
            if (end == total) break;
        }
        return List.copyOf(parts);
    }

    /** Estimate only: one token per CJK code point, one per four other code points, rounded up. */
    public int estimateTokens(String content) {
        int cjk = 0, other = 0;
        for (int offset = 0; offset < content.length();) {
            int cp = content.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) cjk++;
            else other++;
            offset += Character.charCount(cp);
        }
        return Math.max(1, cjk + (other + 3) / 4);
    }

    public record Part(int index, String content, int estimatedTokenCount) {}
}
