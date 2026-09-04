package com.techstack.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techstack.agent.entity.UserToken;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * user_tokens 表 Mapper。
 */
public interface UserTokenMapper extends BaseMapper<UserToken> {

    @Select("SELECT * FROM user_tokens WHERE user_id = #{userId} LIMIT 1")
    UserToken findLatestByUserId(@Param("userId") Long userId);

    /**
     * 单行 upsert：每个用户只保留一条 token（靠 user_id 唯一约束），覆盖旧 token。
     */
    @Insert("""
            INSERT INTO user_tokens (user_id, access_token, token_type, scope, expires_at)
            VALUES (#{userId}, #{accessToken}, #{tokenType}, #{scope}, #{expiresAt})
            ON CONFLICT (user_id)
            DO UPDATE SET access_token = EXCLUDED.access_token,
                          token_type = EXCLUDED.token_type,
                          scope = EXCLUDED.scope,
                          expires_at = EXCLUDED.expires_at,
                          updated_at = now()
            """)
    int upsertByUserId(UserToken token);

    /**
     * 清理指定用户的全部 token（解绑 / 失效时使用）。
     */
    @Delete("DELETE FROM user_tokens WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
