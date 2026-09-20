package com.agentops.analytics.application;

import com.agentops.analytics.api.AnalyticsOverviewResponse;
import com.agentops.analytics.infrastructure.AnalyticsOverviewQueries;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Service
public class AnalyticsOverviewService {
    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private final AnalyticsOverviewQueries queries;

    public AnalyticsOverviewService(AnalyticsOverviewQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(String fromText, String toText, AgentOpsPrincipal principal) {
        if (principal == null || !principal.roles().contains("ADMIN")) {
            throw new AccessDeniedException("Administrator role required");
        }
        Instant from = parse(fromText);
        Instant to = parse(toText);
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "时间范围必须大于零且不超过 90 天");
        }

        String tenantId = principal.tenantId();
        var tickets = queries.tickets(tenantId, from, to);
        var distribution = queries.ticketStatusDistribution(tenantId, from, to);
        var runs = queries.agentRuns(tenantId, from, to);
        var suggestions = queries.suggestions(tenantId, from, to);
        return new AnalyticsOverviewResponse(from, to,
                new AnalyticsOverviewResponse.TicketMetrics(tickets.createdCount(), distribution,
                        tickets.firstRespondedCount(), tickets.averageFirstResponseMillis(),
                        tickets.resolvedOrClosedCount(), rate(tickets.resolvedOrClosedCount(), tickets.createdCount())),
                new AnalyticsOverviewResponse.AgentRunMetrics(runs.completedCount(), runs.succeededCount(),
                        rate(runs.succeededCount(), runs.completedCount())),
                new AnalyticsOverviewResponse.SuggestionMetrics(suggestions.generatedCount(),
                        suggestions.adoptedCount(), rate(suggestions.adoptedCount(), suggestions.generatedCount()),
                        suggestions.sentAdoptedCount(), rate(suggestions.sentAdoptedCount(), suggestions.adoptedCount())));
    }

    private static Instant parse(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from 和 to 必须是带时区的 ISO-8601 时间");
        }
    }

    private static BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.valueOf(0, 4)
                : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
