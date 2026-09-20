package com.agentops.shared.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "请求参数不合法"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401", "请先登录"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "Access Token 无效"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "Access Token 已过期，请重新登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403", "无权执行该操作"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "资源状态冲突"),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在"),
    TICKET_INVALID_TRANSITION(HttpStatus.CONFLICT, "TICKET_INVALID_TRANSITION", "非法的工单状态转换"),
    TICKET_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "TICKET_VERSION_CONFLICT", "工单已被其他请求修改，请刷新后重试"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "幂等请求冲突"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "系统暂时不可用");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
