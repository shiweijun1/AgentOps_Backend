package com.agentops.knowledge.application;

import com.agentops.knowledge.domain.KnowledgeChunkingStrategy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic structure-aware recursive chunking for manually authored knowledge text. */
@Component
public class KnowledgeChunker {
    public static final int MAX_ARTICLE_CODE_POINTS = 20_000;
    public static final int TARGET_CHUNK_TOKENS = 400;
    public static final int MAX_CHUNK_TOKENS = 600;
    public static final int MAX_CHUNKS = 80;

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)(?:\\s+#+)?$");
    private static final Pattern CHINESE_HEADING = Pattern.compile("^第.{1,20}[章节篇部](?:[：:、.．\\s].*)?$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^([0-9]+(?:\\.[0-9]+)*)[、.．]\\s*\\S.*$");
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("(?:\\R[\\p{Zs}\\t]*){2,}");

    public List<Part> split(String articleTitle, String content) {
        if (articleTitle == null || articleTitle.isBlank())
            throw new IllegalArgumentException("Knowledge article title is empty");
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Knowledge content is empty");
        int total = content.codePointCount(0, content.length());
        if (total > MAX_ARTICLE_CODE_POINTS)
            throw new IllegalArgumentException("Knowledge article is too large");

        List<Part> result = new ArrayList<>();
        for (Section section : sections(content)) {
            List<Span> atoms = new ArrayList<>();
            for (Span paragraph : splitParagraphs(content, section.start(), section.end())) {
                splitRecursively(content, paragraph, Boundary.SENTENCE, atoms);
            }
            for (Span span : mergeToTarget(content, atoms)) {
                String cleanContent = clean(content.substring(span.start(), span.end()));
                if (cleanContent.isEmpty()) continue;
                int startOffset = content.codePointCount(0, span.start());
                int endOffset = content.codePointCount(0, span.end());
                String sectionPath = String.join(" > ", section.path());
                String searchText = joinSearchText(articleTitle.strip(), sectionPath, cleanContent);
                String hash = sha256(KnowledgeChunkingStrategy.SEMANTIC_BOUNDARY_V2.name()
                        + "\n" + sectionPath + "\n" + startOffset + ":" + endOffset + "\n" + cleanContent);
                result.add(new Part(result.size(), cleanContent, searchText, sectionPath,
                        startOffset, endOffset, KnowledgeChunkingStrategy.SEMANTIC_BOUNDARY_V2,
                        hash, estimateTokens(cleanContent)));
                if (result.size() > MAX_CHUNKS)
                    throw new IllegalArgumentException("Too many knowledge chunks");
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Knowledge content has no referenceable body");
        return List.copyOf(result);
    }

    private List<Section> sections(String content) {
        List<Section> sections = new ArrayList<>();
        List<String> path = new ArrayList<>();
        int bodyStart = 0;
        int lineStart = 0;
        while (lineStart < content.length()) {
            int lineEnd = lineEnd(content, lineStart);
            Heading heading = heading(content.substring(lineStart, lineEnd).strip());
            int nextLine = nextLineStart(content, lineEnd);
            if (heading != null) {
                addSection(sections, content, bodyStart, lineStart, path);
                while (path.size() >= heading.level()) path.removeLast();
                while (path.size() < heading.level() - 1) path.add("");
                path.add(heading.title());
                bodyStart = nextLine;
            }
            lineStart = nextLine;
        }
        addSection(sections, content, bodyStart, content.length(), path);
        return sections;
    }

    private void addSection(List<Section> sections, String content, int start, int end, List<String> path) {
        Span trimmed = trim(content, new Span(start, end));
        if (trimmed.start() < trimmed.end()) {
            sections.add(new Section(trimmed.start(), trimmed.end(),
                    path.stream().filter(value -> !value.isBlank()).toList()));
        }
    }

    private Heading heading(String line) {
        if (line.isEmpty()) return null;
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) return new Heading(markdown.group(1).length(), markdown.group(2).strip());
        if (CHINESE_HEADING.matcher(line).matches()) return new Heading(1, line);
        Matcher numbered = NUMBERED_HEADING.matcher(line);
        if (numbered.matches()) {
            int level = Math.min(6, numbered.group(1).split("\\.").length);
            return new Heading(level, line);
        }
        return null;
    }

    private List<Span> splitParagraphs(String content, int start, int end) {
        List<Span> result = new ArrayList<>();
        Matcher matcher = PARAGRAPH_BOUNDARY.matcher(content);
        matcher.region(start, end);
        int cursor = start;
        while (matcher.find()) {
            addTrimmed(result, content, cursor, matcher.start());
            cursor = matcher.end();
        }
        addTrimmed(result, content, cursor, end);
        return result;
    }

    private void splitRecursively(String content, Span source, Boundary boundary, List<Span> result) {
        Span span = trim(content, source);
        if (span.start() >= span.end()) return;
        if (estimateTokens(content.substring(span.start(), span.end())) <= MAX_CHUNK_TOKENS) {
            result.add(span);
            return;
        }
        if (boundary == Boundary.HARD) {
            hardSplit(content, span, result);
            return;
        }
        List<Span> split = splitAfterDelimiters(content, span, boundary.delimiters());
        if (split.size() == 1) {
            splitRecursively(content, span, boundary.next(), result);
            return;
        }
        for (Span part : split) splitRecursively(content, part, boundary.next(), result);
    }

    private List<Span> splitAfterDelimiters(String content, Span span, String delimiters) {
        List<Span> result = new ArrayList<>();
        int cursor = span.start();
        for (int offset = span.start(); offset < span.end();) {
            int cp = content.codePointAt(offset);
            offset += Character.charCount(cp);
            if (delimiters.indexOf(cp) >= 0) {
                addTrimmed(result, content, cursor, offset);
                cursor = offset;
            }
        }
        addTrimmed(result, content, cursor, span.end());
        return result;
    }

    private void hardSplit(String content, Span span, List<Span> result) {
        int cursor = span.start();
        while (cursor < span.end()) {
            int end = cursor;
            while (end < span.end()) {
                int next = end + Character.charCount(content.codePointAt(end));
                if (estimateTokens(content.substring(cursor, next)) > MAX_CHUNK_TOKENS && end > cursor) break;
                end = next;
            }
            Span part = trim(content, new Span(cursor, end));
            if (part.start() < part.end()) result.add(part);
            cursor = end;
        }
    }

    private List<Span> mergeToTarget(String content, List<Span> atoms) {
        if (atoms.isEmpty()) return List.of();
        List<Span> merged = new ArrayList<>();
        Span current = atoms.getFirst();
        for (int index = 1; index < atoms.size(); index++) {
            Span next = atoms.get(index);
            Span candidate = new Span(current.start(), next.end());
            if (estimateTokens(clean(content.substring(candidate.start(), candidate.end()))) <= TARGET_CHUNK_TOKENS) {
                current = candidate;
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private void addTrimmed(List<Span> result, String content, int start, int end) {
        Span span = trim(content, new Span(start, end));
        if (span.start() < span.end()) result.add(span);
    }

    private Span trim(String content, Span span) {
        int start = span.start();
        int end = span.end();
        while (start < end) {
            int cp = content.codePointAt(start);
            if (!Character.isWhitespace(cp)) break;
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = content.codePointBefore(end);
            if (!Character.isWhitespace(cp)) break;
            end -= Character.charCount(cp);
        }
        return new Span(start, end);
    }

    private int lineEnd(String content, int start) {
        int lf = content.indexOf('\n', start);
        int cr = content.indexOf('\r', start);
        if (lf < 0) return cr < 0 ? content.length() : cr;
        return cr < 0 ? lf : Math.min(lf, cr);
    }

    private int nextLineStart(String content, int lineEnd) {
        int next = lineEnd;
        if (next < content.length() && content.charAt(next) == '\r') next++;
        if (next < content.length() && content.charAt(next) == '\n') next++;
        return next;
    }

    private String clean(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private String joinSearchText(String title, String path, String content) {
        return path.isBlank() ? title + "\n" + content : title + "\n" + path + "\n" + content;
    }

    /** Estimate only: one token per CJK code point, one per four other code points, rounded up. */
    public int estimateTokens(String content) {
        int cjk = 0, other = 0;
        for (int offset = 0; offset < content.length();) {
            int cp = content.codePointAt(offset);
            if (isCjk(cp)) cjk++; else other++;
            offset += Character.charCount(cp);
        }
        return Math.max(1, cjk + (other + 3) / 4);
    }

    private boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private enum Boundary {
        SENTENCE("。！？!?．."), PUNCTUATION("；;，,、：:"), HARD("");
        private final String delimiters;
        Boundary(String delimiters) { this.delimiters = delimiters; }
        String delimiters() { return delimiters; }
        Boundary next() { return this == SENTENCE ? PUNCTUATION : HARD; }
    }

    private record Heading(int level, String title) {}
    private record Section(int start, int end, List<String> path) {}
    private record Span(int start, int end) {}

    public record Part(int index, String content, String searchText, String sectionPath,
                       int startOffset, int endOffset, KnowledgeChunkingStrategy strategy,
                       String chunkHash, int estimatedTokenCount) {}
}
