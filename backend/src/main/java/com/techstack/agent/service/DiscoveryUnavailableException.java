package com.techstack.agent.service;

/** 无法核验与明确不属于技术栈分开处理；失败时禁止降级入库。 */
public class DiscoveryUnavailableException extends RuntimeException {
    public DiscoveryUnavailableException() {
        super("暂时无法确认该技术栈及其官方来源，尚未收录。请使用准确名称或稍后重试。");
    }
}
