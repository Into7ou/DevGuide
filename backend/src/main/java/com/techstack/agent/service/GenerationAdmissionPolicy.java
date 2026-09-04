package com.techstack.agent.service;

/**
 * 生成调用的成本准入边界。未来可用 Redis/队列实现替换当前单实例实现。
 */
public interface GenerationAdmissionPolicy {

    Permit acquire(String subject, Operation operation);

    enum Operation {
        AGENT,
        DYNAMIC_SEARCH
    }

    interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
