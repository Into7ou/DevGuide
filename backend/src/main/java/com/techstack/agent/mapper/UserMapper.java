package com.techstack.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techstack.agent.entity.User;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * users 表 Mapper。
 */
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE github_id = #{githubId} LIMIT 1")
    User findByGithubId(@Param("githubId") Long githubId);

    /**
     * 原子 upsert：并发首次登录时靠唯一约束 + ON CONFLICT 合并，避免 check-then-insert 竞态。
     */
    @Insert("""
            INSERT INTO users (github_id, username, avatar_url)
            VALUES (#{githubId}, #{username}, #{avatarUrl})
            ON CONFLICT (github_id)
            DO UPDATE SET username = EXCLUDED.username,
                          avatar_url = EXCLUDED.avatar_url,
                          updated_at = now()
            """)
    int upsertByGithubId(@Param("githubId") Long githubId,
                         @Param("username") String username,
                         @Param("avatarUrl") String avatarUrl);
}
