package com.techstack.agent.config;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.techstack.agent.security.CustomOAuth2UserService;
import com.techstack.agent.security.OAuth2SuccessHandler;

@SpringJUnitWebConfig(SecurityConfigTest.TestApplication.class)
class SecurityConfigTest {

    private final WebApplicationContext context;
    private MockMvc mvc;

    SecurityConfigTest(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void healthAndAuthenticationProbeStayPublic() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/showcase/tech-stacks"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousBusinessApiGetsJson401InsteadOfOAuthRedirect() throws Exception {
        mvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication_required"));
    }

    @Test
    void authenticatedBusinessApiIsAllowed() throws Exception {
        mvc.perform(get("/api/v1/probe").with(user("developer")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedPostRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/probe").with(user("developer")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/probe")
                        .with(user("developer"))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestApplication {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        CustomOAuth2UserService customOAuth2UserService() {
            return mock(CustomOAuth2UserService.class);
        }

        @Bean
        OAuth2SuccessHandler oauth2SuccessHandler() {
            return mock(OAuth2SuccessHandler.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return mock(ClientRegistrationRepository.class);
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/health")
        String health() {
            return "UP";
        }

        @GetMapping("/api/auth/me")
        String me() {
            return "anonymous";
        }

        @GetMapping("/api/v1/probe")
        String protectedApi() {
            return "ok";
        }

        @GetMapping("/api/v1/showcase/tech-stacks")
        String showcase() {
            return "[]";
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/probe")
        String protectedMutation() {
            return "ok";
        }
    }
}
