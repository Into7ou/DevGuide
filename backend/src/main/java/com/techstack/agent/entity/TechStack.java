package com.techstack.agent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.techstack.agent.handler.JsonbTypeHandler;

import lombok.Getter;
import lombok.Setter;

/**
 * 技术栈预置清单实体，对应表 tech_stacks。
 */
@Getter
@Setter
@TableName(value = "tech_stacks", autoResultMap = true)
public class TechStack {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 显示名，如 "React" */
    private String name;

    /** 逗号分隔别名，如 "reactjs,react.js" */
    private String aliases;

    /** 官方文档首页 URL */
    private String officialDocUrl;

    /** JSONB 原始 JSON 文本，形如 ["url1","url2"]，由 Service 解析为 List&lt;String&gt; */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String docKeyPages;

    /** GitHub 搜索 query，如 "topic:react" */
    private String githubSearchQuery;

    /** 一句话简介 */
    private String description;

    /** 分类：frontend / backend / ml-data / infra（侧边栏导航 + 首页筛选） */
    private String category;

    /** RAG 入库时间，null 表示尚未入库 */
    private LocalDateTime ragIngestedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
