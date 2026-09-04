package com.techstack.agent.service;

/** 公开展示只允许读取已收录条目，未知名称不得回退到动态发现。 */
public class ShowcaseNotFoundException extends RuntimeException {

    public ShowcaseNotFoundException() {
        super("该技术栈尚未收录");
    }
}
