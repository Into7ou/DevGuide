package com.techstack.agent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户 GitHub Token 绑定实体，对应表 user_tokens。
 * accessToken 为加密存储，使用时通过 TokenCipher 解密。
 */
@Getter
@Setter
@TableName("user_tokens")
public class UserToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 加密后的 OAuth access token */
    private String accessToken;

    private String tokenType;

    private String scope;

    /** token 过期时间（GitHub fine-grained token 会过期；classic token 通常为 null 表示不过期） */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
