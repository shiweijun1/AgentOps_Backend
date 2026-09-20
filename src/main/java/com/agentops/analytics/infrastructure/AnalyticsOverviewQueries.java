package com.agentops.analytics.infrastructure;

import com.agentops.ticket.domain.TicketStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

@Repository
public class AnalyticsOverviewQueries {
    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsOverviewQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TicketCounts tickets(String tenantId, Instant from, Instant to) {
        String sql = """
                SELECT COUNT(*) AS created_count,
                       COALESCE(SUM(CASE WHEN first_response_at IS NOT NULL AND submitted_at IS NOT NULL
                                         AND first_response_at >= submitted_at THEN 1 ELSE 0 END), 0) AS responded_count,
                       AVG(CASE WHEN first_response_at IS NOT NULL AND submitted_at IS NOT NULL
                                AND first_response_at >= submitted_at
                                THEN TIMESTAMPDIFF(MICROSECOND, submitted_at, first_response_at) END) AS average_response_us,
                       COALESCE(SUM(CASE WHEN status IN ('RESOLVED', 'CLOSED') THEN 1 ELSE 0 END), 0) AS resolved_count
                  FROM ticket
                 WHERE tenant_id = :tenantId AND created_at >= :fromUtc AND created_at < :toUtc
                """;
        return jdbc.queryForObject(sql, parameters(tenantId, from, to), (rs, rowNum) -> {
            BigDecimal averageMicros = rs.getBigDecimal("average_response_us");
            return new TicketCounts(rs.getLong("created_count"), rs.getLong("responded_count"),
                    averageMicros == null ? null : averageMicros.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP),
                    rs.getLong("resolved_count"));
        });
    }

    public Map<TicketStatus, Long> ticketStatusDistribution(String tenantId, Instant from, Instant to) {
        EnumMap<TicketStatus, Long> result = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) result.put(status, 0L);
        String sql = """
                SELECT status, COUNT(*) AS ticket_count FROM ticket
                 WHERE tenant_id = :tenantId AND created_at >= :fromUtc AND created_at < :toUtc
                 GROUP BY status
                """;
        jdbc.query(sql, parameters(tenantId, from, to), rs -> {
            result.put(TicketStatus.valueOf(rs.getString("status")), rs.getLong("ticket_count"));
        });
        return Map.copyOf(result);
    }

    public AgentRunCounts agentRuns(String tenantId, Instant from, Instant to) {
        String sql = """
                SELECT COUNT(*) AS completed_count,
                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END), 0) AS succeeded_count
                  FROM agent_run
                 WHERE tenant_id = :tenantId AND finished_at >= :fromUtc AND finished_at < :toUtc
                   AND status IN ('SUCCEEDED', 'FAILED')
                """;
        return jdbc.queryForObject(sql, parameters(tenantId, from, to), (rs, rowNum) ->
                new AgentRunCounts(rs.getLong("completed_count"), rs.getLong("succeeded_count")));
    }

    public SuggestionCounts suggestions(String tenantId, Instant from, Instant to) {
        String sql = """
                SELECT COUNT(*) AS generated_count,
                       COALESCE(SUM(CASE WHEN s.status = 'ADOPTED' THEN 1 ELSE 0 END), 0) AS adopted_count,
                       COALESCE(SUM(CASE WHEN s.status = 'ADOPTED' AND m.id IS NOT NULL THEN 1 ELSE 0 END), 0) AS sent_count
                  FROM ai_suggestion s
                  LEFT JOIN ticket_message m
                    ON m.id = s.source_message_id AND m.tenant_id = s.tenant_id AND m.ticket_id = s.ticket_id
                   AND m.source_suggestion_id = s.id AND m.message_type = 'AI_SUGGESTION'
                   AND m.visible_to_requester = TRUE
                 WHERE s.tenant_id = :tenantId AND s.created_at >= :fromUtc AND s.created_at < :toUtc
                """;
        return jdbc.queryForObject(sql, parameters(tenantId, from, to), (rs, rowNum) ->
                new SuggestionCounts(rs.getLong("generated_count"), rs.getLong("adopted_count"), rs.getLong("sent_count")));
    }

    private static MapSqlParameterSource parameters(String tenantId, Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("fromUtc", LocalDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("toUtc", LocalDateTime.ofInstant(to, ZoneOffset.UTC));
    }

    public record TicketCounts(long createdCount, long firstRespondedCount,
                               BigDecimal averageFirstResponseMillis, long resolvedOrClosedCount) {}
    public record AgentRunCounts(long completedCount, long succeededCount) {}
    public record SuggestionCounts(long generatedCount, long adoptedCount, long sentAdoptedCount) {}
}
