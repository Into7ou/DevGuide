package com.techstack.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InMemoryGenerationAdmissionPolicyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void springCreatesConfiguredPolicyBean() {
        new ApplicationContextRunner()
                .withBean(InMemoryGenerationAdmissionPolicy.class)
                .withPropertyValues(
                        "app.generation-admission.enabled=true",
                        "app.generation-admission.agent-daily-limit=10",
                        "app.generation-admission.discovery-daily-limit=20",
                        "app.generation-admission.max-concurrent=2")
                .run(context -> assertThat(context).hasSingleBean(InMemoryGenerationAdmissionPolicy.class));
    }

    @Test
    void isolatesDailyQuotaByUserAndOperation() {
        var policy = new InMemoryGenerationAdmissionPolicy(true, 1, 1, 2, CLOCK);
        policy.acquire("github:1", GenerationAdmissionPolicy.Operation.AGENT).close();

        assertThatThrownBy(() -> policy.acquire("github:1", GenerationAdmissionPolicy.Operation.AGENT))
                .isInstanceOf(GenerationRejectedException.class)
                .hasMessageContaining("UTC");
        assertThatCode(() -> policy.acquire("github:2", GenerationAdmissionPolicy.Operation.AGENT).close())
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.acquire("github:1", GenerationAdmissionPolicy.Operation.DYNAMIC_SEARCH).close())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsThirdConcurrentRequestAndCloseIsIdempotent() {
        var policy = new InMemoryGenerationAdmissionPolicy(true, 10, 10, 2, CLOCK);
        var first = policy.acquire("github:1", GenerationAdmissionPolicy.Operation.AGENT);
        var second = policy.acquire("github:2", GenerationAdmissionPolicy.Operation.AGENT);

        assertThatThrownBy(() -> policy.acquire("github:3", GenerationAdmissionPolicy.Operation.AGENT))
                .isInstanceOf(GenerationRejectedException.class)
                .hasMessageContaining("正忙");

        first.close();
        first.close();
        assertThatCode(() -> policy.acquire("github:3", GenerationAdmissionPolicy.Operation.AGENT).close())
                .doesNotThrowAnyException();
        second.close();
    }

    @Test
    void disabledPolicyDoesNotLimitDevelopment() {
        var policy = new InMemoryGenerationAdmissionPolicy(false, 1, 1, 1, CLOCK);
        var first = policy.acquire("github:1", GenerationAdmissionPolicy.Operation.AGENT);
        assertThatCode(() -> policy.acquire("github:1", GenerationAdmissionPolicy.Operation.AGENT).close())
                .doesNotThrowAnyException();
        first.close();
    }
}
