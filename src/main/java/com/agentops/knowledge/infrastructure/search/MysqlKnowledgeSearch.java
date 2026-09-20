package com.agentops.knowledge.infrastructure.search;

import com.agentops.knowledge.application.KnowledgeSearchHit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlKnowledgeSearch {
    private static final String SQL = """
            SELECT a.id AS article_id, v.id AS version_id, c.id AS chunk_id,
                   a.title, c.content AS snippet,
                   MATCH(c.content) AGAINST (:keyword IN NATURAL LANGUAGE MODE) AS score
              FROM knowledge_chunk c
              JOIN knowledge_version v ON v.id = c.knowledge_version_id
              JOIN knowledge_article a ON a.id = v.article_id
             WHERE a.tenant_id = :tenantId
               AND v.tenant_id = :tenantId
               AND c.tenant_id = :tenantId
               AND a.status = 'PUBLISHED'
               AND v.review_status = 'PUBLISHED'
               AND c.index_status = 'INDEXED'
               AND a.current_version_id = v.id
               AND (a.valid_from IS NULL OR a.valid_from <= :now)
               AND (a.valid_until IS NULL OR a.valid_until > :now)
               AND MATCH(c.content) AGAINST (:keyword IN NATURAL LANGUAGE MODE) > 0
             ORDER BY score DESC, a.id, c.chunk_index
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public MysqlKnowledgeSearch(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<KnowledgeSearchHit> search(String tenantId, String keyword, Instant now, int limit) {
        var parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("keyword", keyword)
                .addValue("now", java.time.LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                .addValue("limit", limit);
        return jdbc.query(SQL, parameters, (rs, row) -> new KnowledgeSearchHit(
                uuid(rs.getBytes("article_id")), uuid(rs.getBytes("version_id")),
                uuid(rs.getBytes("chunk_id")), rs.getString("title"),
                rs.getString("snippet"), rs.getDouble("score")));
    }

    private UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
