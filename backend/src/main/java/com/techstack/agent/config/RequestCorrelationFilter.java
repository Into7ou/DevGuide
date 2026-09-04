package com.techstack.agent.config;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/** 生成或接收可信反向代理的请求 ID；仅允许 UUID，避免日志注入与无限长度字段。 */
@Slf4j
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String PROXY_HEADER_NAME = "X-Proxy-Request-ID";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = trustedProxyRequestId(request);
        long startedAt = System.nanoTime();
        response.setHeader(HEADER_NAME, requestId);
        MDC.put("requestId", requestId);
        try {
            chain.doFilter(request, response);
            if (request.isAsyncStarted()) {
                registerAsyncLog(request, response, requestId, startedAt);
            } else {
                logRequest(request, response, requestId, startedAt, null);
            }
        } finally {
            MDC.remove("requestId");
        }
    }

    private String trustedProxyRequestId(HttpServletRequest request) {
        String candidate = request.getHeader(PROXY_HEADER_NAME);
        return candidate != null && UUID_PATTERN.matcher(candidate).matches()
                ? candidate.toLowerCase()
                : UUID.randomUUID().toString();
    }

    private void registerAsyncLog(HttpServletRequest request, HttpServletResponse response, String requestId,
                                  long startedAt) {
        AtomicBoolean logged = new AtomicBoolean();
        request.getAsyncContext().addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                logOnce(null);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                logOnce("async_timeout");
            }

            @Override
            public void onError(AsyncEvent event) {
                Throwable throwable = event.getThrowable();
                logOnce(throwable == null ? "async_error" : throwable.getClass().getSimpleName());
            }

            @Override
            public void onStartAsync(AsyncEvent event) { }

            private void logOnce(String errorType) {
                if (logged.compareAndSet(false, true)) {
                    logRequest(request, response, requestId, startedAt, errorType);
                }
            }
        });
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, String requestId,
                            long startedAt, String errorType) {
        if ("/api/health".equals(request.getRequestURI()) && response.getStatus() < 400) return;
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("request id={} method={} path={} status={} durationMs={} errorType={}",
                requestId, request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs,
                errorType == null ? "none" : errorType);
    }
}
