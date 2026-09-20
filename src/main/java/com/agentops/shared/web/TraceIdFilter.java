package com.agentops.shared.web;

import com.agentops.shared.api.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TraceIds.HEADER_NAME);
        String traceId = StringUtils.hasText(incomingTraceId)
                ? incomingTraceId.substring(0, Math.min(incomingTraceId.length(), 64))
                : UUID.randomUUID().toString();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(TraceIds.MDC_KEY, traceId)) {
            response.setHeader(TraceIds.HEADER_NAME, traceId);
            filterChain.doFilter(request, response);
        }
    }
}
