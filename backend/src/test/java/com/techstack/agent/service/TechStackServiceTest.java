package com.techstack.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.dto.TechStackOverviewDto;
import com.techstack.agent.entity.TechStack;
import com.techstack.agent.mapper.TechStackMapper;

/**
 * 验证技术栈聚合逻辑：映射表命中与核验失败不降级为成功。
 */
class TechStackServiceTest {

    private TechStackMapper mapper;
    private GithubService githubService;
    private TechStackDiscoveryService discoveryService;
    private TechStackService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TechStackMapper.class);
        githubService = mock(GithubService.class);
        discoveryService = mock(TechStackDiscoveryService.class);
        service = new TechStackService(mapper, githubService, new ObjectMapper(), discoveryService);
    }

    @Test
    void getOverviewUsesMappedStackAndParsesKeyPages() {
        TechStack stack = new TechStack();
        stack.setName("React");
        stack.setDescription("UI library");
        stack.setOfficialDocUrl("https://react.dev");
        stack.setGithubSearchQuery("topic:react");
        stack.setDocKeyPages("[\"https://react.dev/learn\"]");

        when(mapper.findByNameIgnoreCase("react")).thenReturn(stack);
        when(githubService.topRepos("topic:react")).thenReturn(List.of(
                new GithubRepoDto("react", "facebook/react", null, 1, null,
                        "https://github.com/facebook/react", null, null, null)));

        TechStackOverviewDto overview = service.getOverview("react");

        assertThat(overview.name()).isEqualTo("React");
        assertThat(overview.officialDocUrl()).isEqualTo("https://react.dev");
        assertThat(overview.docKeyPages()).containsExactly("https://react.dev/learn");
        assertThat(overview.topRepos()).hasSize(1);
    }

    @Test
    void getOverviewPropagatesUnverifiedDiscoveryWithoutSearchingGithub() {
        when(mapper.findByNameIgnoreCase(anyString())).thenReturn(null);
        when(mapper.findByAlias(anyString())).thenReturn(null);
        when(discoveryService.discover(anyString())).thenThrow(new DiscoveryUnavailableException());
        assertThatThrownBy(() -> service.getOverview("foo")).isInstanceOf(DiscoveryUnavailableException.class);
        verifyNoInteractions(githubService);
    }
}
