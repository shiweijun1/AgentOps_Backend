package com.agentops.knowledge.infrastructure.search;

import com.agentops.knowledge.application.KnowledgeCitationVerifier;
import com.agentops.knowledge.application.VerifiedKnowledgeChunk;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlKnowledgeCitationVerifier implements KnowledgeCitationVerifier {
    private static final String SQL = """
            SELECT a.id AS article_id, v.id AS version_id, c.id AS chunk_id,
                   a.title, c.content
              FROM knowledge_article a
              JOIN knowledge_version v ON v.article_id = a.id
              JOIN knowledge_chunk c ON c.knowledge_version_id = v.id
             WHERE a.tenant_id = :tenantId AND v.tenant_id = :tenantId AND c.tenant_id = :tenantId
               AND c.id IN (:chunkIds)
               AND a.status = 'PUBLISHED' AND v.review_status = 'PUBLISHED'
               AND c.index_status = 'INDEXED' AND a.current_version_id = v.id
               AND (a.valid_from IS NULL OR a.valid_from <= :now)
               AND (a.valid_until IS NULL OR a.valid_until > :now)
             FOR SHARE
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public MysqlKnowledgeCitationVerifier(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<VerifiedKnowledgeChunk> lockCurrent(String tenantId, List<UUID> chunkIds, Instant now) {
        if (chunkIds.isEmpty()) return List.of();
        var params = new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("chunkIds", chunkIds.stream().map(this::bytes).toList())
                .addValue("now", java.time.LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
        return jdbc.query(SQL, params, (rs, row) -> new VerifiedKnowledgeChunk(
                uuid(rs.getBytes("article_id")), uuid(rs.getBytes("version_id")),
                uuid(rs.getBytes("chunk_id")), rs.getString("title"), rs.getString("content")));
    }

    private byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    private UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
