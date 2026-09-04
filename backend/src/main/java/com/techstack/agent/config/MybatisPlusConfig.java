package com.techstack.agent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：扫描 Mapper 接口。
 */
@Configuration
@MapperScan("com.techstack.agent.mapper")
public class MybatisPlusConfig {
}
