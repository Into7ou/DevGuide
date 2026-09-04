package com.techstack.agent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体，对应表 users。
 */
@Getter
@Setter
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** GitHub 用户唯一 ID */
    private Long githubId;

    /** GitHub 用户名（login） */
    private String username;

    private String avatarUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
