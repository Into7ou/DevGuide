package com.techstack.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigTest {

    @Test
    void productionProfileEnforcesSecureSessionCookieAndProxyHeaders() throws Exception {
        ClassPathResource resource = new ClassPathResource("application-prod.yml");
        assertTrue(resource.exists(), "缺少生产 profile");

        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("production", resource).forEach(sources::addLast);
        PropertySourcesPropertyResolver properties = new PropertySourcesPropertyResolver(sources);

        assertEquals("true", properties.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("true", properties.getProperty("server.servlet.session.cookie.http-only"));
        assertEquals("lax", properties.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("framework", properties.getProperty("server.forward-headers-strategy"));
        assertEquals("true", properties.getProperty("app.deployment.single-instance"));
    }

    @Test
    void productionEnvironmentTemplateListsEveryRequiredSecretAndProfile() throws Exception {
        Path template = Path.of("..", ".env.production.example");
        assertTrue(Files.exists(template), "缺少生产环境变量模板");
        String text = Files.readString(template, StandardCharsets.UTF_8);

        for (String key : List.of(
                "SPRING_PROFILES_ACTIVE=prod",
                "DEEPSEEK_API_KEY=",
                "DASHSCOPE_API_KEY=",
                "TAVILY_SEARCH_API_KEY=",
                "GITHUB_OAUTH_CLIENT_ID=",
                "GITHUB_OAUTH_CLIENT_SECRET=",
                "TOKEN_CIPHER_KEY=",
                "TOKEN_CIPHER_PREVIOUS_KEYS=",
                "POSTGRES_PASSWORD=",
                "FRONTEND_BASE_URL=")) {
            assertTrue(text.contains(key), "生产模板缺少 " + key);
        }
    }
}
