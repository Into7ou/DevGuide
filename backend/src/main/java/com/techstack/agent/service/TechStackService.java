package com.techstack.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.dto.TechStackDto;
import com.techstack.agent.dto.TechStackOverviewDto;
import com.techstack.agent.entity.TechStack;
import com.techstack.agent.mapper.TechStackMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 技术栈查询服务：官方文档映射 + GitHub Top10 聚合。
 */
@Slf4j
@Service
public class TechStackService {

    private final TechStackMapper techStackMapper;
    private final GithubService githubService;
    private final ObjectMapper objectMapper;
    private final TechStackDiscoveryService discoveryService;

    public TechStackService(TechStackMapper techStackMapper, GithubService githubService,
                            ObjectMapper objectMapper, TechStackDiscoveryService discoveryService) {
        this.techStackMapper = techStackMapper;
        this.githubService = githubService;
        this.objectMapper = objectMapper;
        this.discoveryService = discoveryService;
    }

    public List<TechStackDto> listAll() {
        return techStackMapper.selectList(
                        Wrappers.<TechStack>lambdaQuery().orderByAsc(TechStack::getName))
                .stream()
                .map(e -> new TechStackDto(e.getId(), e.getName(), e.getDescription(), e.getOfficialDocUrl(),
                        e.getCategory()))
                .toList();
    }

    public TechStackOverviewDto getOverview(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.trim().length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("技术栈名称不能超过 100 个字符或包含控制字符。");
        }
        String normalized = TechStackDiscoveryService.normalizeKnownName(name.trim());

        TechStack stack = techStackMapper.findByNameIgnoreCase(normalized);
        if (stack == null) {
            stack = techStackMapper.findByAlias(normalized);
        }

        if (stack == null) {
            return discoverAndUpsert(normalized);
        }

        List<GithubRepoDto> repos = fetchTopRepos(stack);
        List<String> keyPages = parseDocKeyPages(stack.getDocKeyPages());
        return new TechStackOverviewDto(
                stack.getName(), stack.getDescription(), stack.getOfficialDocUrl(), keyPages, stack.getCategory(), repos);
    }

    /**
     * 先核验技术实体及官方来源，再搜 GitHub Top10 并转正。
     * 核验失败必须向上传递，不能被 GitHub 降级逻辑吞掉后继续返回成功。
     */
    private TechStackOverviewDto discoverAndUpsert(String name) {
        TechStackDiscoveryService.DiscoveryResult discovered = discoveryService.discover(name);
        // 别名首次查询可能识别为已有规范名称，直接复用已有记录。
        TechStack existing = techStackMapper.findByNameIgnoreCase(discovered.canonicalName());
        if (existing == null) {
            existing = techStackMapper.findByAlias(discovered.canonicalName());
        }
        if (existing != null) {
            return new TechStackOverviewDto(existing.getName(), existing.getDescription(), existing.getOfficialDocUrl(),
                    parseDocKeyPages(existing.getDocKeyPages()), existing.getCategory(), fetchTopRepos(existing));
        }
        String query = discovered.githubSearchQuery();
        List<GithubRepoDto> repos = safeTopRepos(query);
        techStackMapper.upsertDiscovered(discovered.canonicalName(), discovered.officialDocUrl(),
                discovered.description(), discovered.category(), query);
        return new TechStackOverviewDto(discovered.canonicalName(), discovered.description(),
                discovered.officialDocUrl(), List.of(), discovered.category(), repos);
    }

    private List<GithubRepoDto> fetchTopRepos(TechStack stack) {
        String query = stack.getGithubSearchQuery();
        if (query == null || query.isBlank()) {
            // 防御性兜底：转正记录若缺 query（历史数据），按名称派生
            query = sanitizeSearchTerm(stack.getName()) + " in:name,description";
        }
        return safeTopRepos(query);
    }

    /** 拉取 Top10，上游故障时降级为空列表而非抛出异常。 */
    private List<GithubRepoDto> safeTopRepos(String query) {
        try {
            return githubService.topRepos(query);
        } catch (Exception e) {
            log.warn("拉取 GitHub Top10 失败，降级为空列表: {}", query);
            return List.of();
        }
    }

    /** 去掉可能构造 GitHub 限定符的字符（:、"、'），降低 query 注入风险。 */
    private String sanitizeSearchTerm(String name) {
        return name.replaceAll("[:'\"]", " ").trim();
    }

    private List<String> parseDocKeyPages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse docKeyPages JSON: {}", json, e);
            return List.of();
        }
    }
}
