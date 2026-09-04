package com.techstack.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techstack.agent.dto.TechStackDto;
import com.techstack.agent.dto.TechStackOverviewDto;
import com.techstack.agent.service.TechStackService;

/** 匿名只读展示接口：只返回数据库中已经收录的内容。 */
@RestController
@RequestMapping("/api/v1/showcase/tech-stacks")
public class ShowcaseController {

    private final TechStackService techStackService;

    public ShowcaseController(TechStackService techStackService) {
        this.techStackService = techStackService;
    }

    @GetMapping
    public List<TechStackDto> list() {
        return techStackService.listAll();
    }

    @GetMapping("/{name}")
    public TechStackOverviewDto get(@PathVariable String name) {
        return techStackService.getShowcaseOverview(name);
    }
}
