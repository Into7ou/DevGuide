package com.techstack.agent.service;

/** 生成配额或并发闸门拒绝请求。 */
public class GenerationRejectedException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;

    public GenerationRejectedException(String code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
