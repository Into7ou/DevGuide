package com.techstack.agent.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;
import com.techstack.agent.service.DiscoveryUnavailableException;

/**
 * 全局异常处理：把底层异常转换为友好的 JSON 错误响应。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DiscoveryUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleDiscoveryUnavailable(DiscoveryUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    /** 上游返回 4xx/5xx（如 GitHub/DeepSeek 限流），统一视为网关错误 */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleRestClient(RestClientResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Upstream API error: " + e.getStatusCode().value()));
    }

    /** 网络异常：DNS/连接拒绝/超时 */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> handleResourceAccess(ResourceAccessException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Upstream service unreachable"));
    }

    /** 参数校验失败 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** 请求体缺失 / JSON 语法错误 / 字段类型错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body"));
    }

    /** 兜底：未预期异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
