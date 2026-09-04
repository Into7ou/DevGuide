package com.techstack.agent.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** M7 单实例生成准入；状态不跨实例共享。 */
@Service
public class InMemoryGenerationAdmissionPolicy implements GenerationAdmissionPolicy {

    private static final Permit NOOP = () -> { };

    private final boolean enabled;
    private final int agentDailyLimit;
    private final int discoveryDailyLimit;
    private final Semaphore concurrent;
    private final Clock clock;
    private final Map<CounterKey, AtomicInteger> dailyCounters = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryGenerationAdmissionPolicy(
            @Value("${app.generation-admission.enabled:false}") boolean enabled,
            @Value("${app.generation-admission.agent-daily-limit:10}") int agentDailyLimit,
            @Value("${app.generation-admission.discovery-daily-limit:20}") int discoveryDailyLimit,
            @Value("${app.generation-admission.max-concurrent:2}") int maxConcurrent) {
        this(enabled, agentDailyLimit, discoveryDailyLimit, maxConcurrent, Clock.systemUTC());
    }

    InMemoryGenerationAdmissionPolicy(boolean enabled, int agentDailyLimit, int discoveryDailyLimit,
                                      int maxConcurrent, Clock clock) {
        if (agentDailyLimit < 1 || discoveryDailyLimit < 1 || maxConcurrent < 1) {
            throw new IllegalArgumentException("生成准入限额必须为正整数");
        }
        this.enabled = enabled;
        this.agentDailyLimit = agentDailyLimit;
        this.discoveryDailyLimit = discoveryDailyLimit;
        this.concurrent = new Semaphore(maxConcurrent, true);
        this.clock = clock;
    }

    @Override
    public Permit acquire(String subject, Operation operation) {
        if (!enabled) return NOOP;
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("生成准入缺少认证用户标识");
        }
        if (!concurrent.tryAcquire()) {
            throw new GenerationRejectedException("generation_busy", "生成服务正忙，请稍后再试。", 5);
        }

        boolean quotaAcquired = false;
        try {
            LocalDate today = LocalDate.now(clock);
            evictOldDays(today);
            CounterKey key = new CounterKey(subject, operation, today);
            int limit = operation == Operation.AGENT ? agentDailyLimit : discoveryDailyLimit;
            AtomicInteger counter = dailyCounters.computeIfAbsent(key, ignored -> new AtomicInteger());
            if (!tryIncrement(counter, limit)) {
                throw new GenerationRejectedException("generation_daily_limit",
                        "今日生成额度已用完，请在下一个 UTC 日再试。", secondsUntilNextUtcDay());
            }
            quotaAcquired = true;
            AtomicBoolean released = new AtomicBoolean();
            return () -> {
                if (released.compareAndSet(false, true)) concurrent.release();
            };
        } finally {
            if (!quotaAcquired) concurrent.release();
        }
    }

    private boolean tryIncrement(AtomicInteger counter, int limit) {
        while (true) {
            int current = counter.get();
            if (current >= limit) return false;
            if (counter.compareAndSet(current, current + 1)) return true;
        }
    }

    private void evictOldDays(LocalDate today) {
        dailyCounters.keySet().removeIf(key -> !key.day().equals(today));
    }

    private long secondsUntilNextUtcDay() {
        Instant now = clock.instant();
        Instant nextDay = LocalDate.now(clock).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Math.max(1, Duration.between(now, nextDay).toSeconds());
    }

    private record CounterKey(String subject, Operation operation, LocalDate day) { }
}
