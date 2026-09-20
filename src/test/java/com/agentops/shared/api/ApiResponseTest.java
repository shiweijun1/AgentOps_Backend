package com.agentops.shared.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldBuildSuccessfulResponseWithTraceId() {
        MDC.put(TraceIds.MDC_KEY, "trace-123");

        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.traceId()).isEqualTo("trace-123");
        assertThat(response.timestamp()).isNotNull();
    }
}
