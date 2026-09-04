package com.techstack.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 验证分类常量集合与前端 categories.js、V5 迁移保持一致。
 */
class TechStackDiscoveryServiceTest {

    @Test
    void validCategoriesMatchContract() {
        assertThat(TechStackDiscoveryService.VALID_CATEGORIES)
                .containsExactlyInAnyOrder("frontend", "backend", "ml-data", "infra");
    }
}
