package com.techstack.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.techstack.agent.dto.TechStackDto;
import com.techstack.agent.dto.TechStackOverviewDto;
import com.techstack.agent.service.TechStackService;
import com.techstack.agent.security.AuthenticatedSubject;

/**
 * 技术栈查询接口。
 */
@RestController
@RequestMapping("/api/v1/tech-stacks")
public class TechStackController {

    private final TechStackService techStackService;

    public TechStackController(TechStackService techStackService) {
        this.techStackService = techStackService;
    }

    @GetMapping
    public List<TechStackDto> list() {
        return techStackService.listAll();
    }

    @GetMapping("/{name}")
    public TechStackOverviewDto get(@PathVariable String name, Authentication authentication) {
        return techStackService.getOverview(name, AuthenticatedSubject.key(authentication));
    }
}
