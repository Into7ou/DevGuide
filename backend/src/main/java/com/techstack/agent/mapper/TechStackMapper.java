package com.techstack.agent.mapper;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techstack.agent.entity.TechStack;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * tech_stacks 表 Mapper。
 */
public interface TechStackMapper extends BaseMapper<TechStack> {

    /**
     * 按显示名精确匹配（忽略大小写）。
     */
    @Select("SELECT * FROM tech_stacks WHERE LOWER(name) = LOWER(#{name}) LIMIT 1")
    TechStack findByNameIgnoreCase(@Param("name") String name);

    /**
     * 按别名精确匹配（忽略大小写）：把逗号分隔的 aliases 拆成数组后逐项等值比较，
     * 避免 LIKE 的通配符注入与多结果不确定性。用 ORDER BY id 保证结果确定。
     */
    @Select("SELECT * FROM tech_stacks WHERE aliases IS NOT NULL "
            + "AND LOWER(#{name}) IN (SELECT LOWER(TRIM(alias)) FROM unnest(string_to_array(aliases, ',')) AS alias) "
            + "ORDER BY id LIMIT 1")
    TechStack findByAlias(@Param("name") String name);

    /**
     * 只更新 rag_ingested_at 单列（避免整行回写导致并发丢失更新）。
     */
    @Update("UPDATE tech_stacks SET rag_ingested_at = #{ingestedAt} WHERE id = #{id}")
    int markIngested(@Param("id") Long id, @Param("ingestedAt") LocalDateTime ingestedAt);

    /**
     * 动态转正 upsert：未收录技术栈首次查询后写入正式记录。
     * 名称冲突时更新文档 URL / 简介 / 分类 / 查询词（ON CONFLICT 原子合并）。
     * 注意：冲突目标是 LOWER(name) 表达式唯一索引（大小写不敏感，见 V6 迁移）。
     */
    @Insert("""
            INSERT INTO tech_stacks (name, official_doc_url, description, category, github_search_query)
            VALUES (#{name}, #{officialDocUrl}, #{description}, #{category}, #{githubSearchQuery})
            ON CONFLICT (LOWER(name))
            DO UPDATE SET official_doc_url = COALESCE(EXCLUDED.official_doc_url, tech_stacks.official_doc_url),
                          description = COALESCE(EXCLUDED.description, tech_stacks.description),
                          category = COALESCE(EXCLUDED.category, tech_stacks.category),
                          github_search_query = COALESCE(EXCLUDED.github_search_query, tech_stacks.github_search_query),
                          updated_at = now()
            """)
    int upsertDiscovered(@Param("name") String name,
                         @Param("officialDocUrl") String officialDocUrl,
                         @Param("description") String description,
                         @Param("category") String category,
                         @Param("githubSearchQuery") String githubSearchQuery);
}
