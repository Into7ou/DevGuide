package com.techstack.agent.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 官方文档关键页抓取：URL -> 纯文本（供 RAG 入库）。
 */
@Component
public class DocsFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    public String fetchText(String url) {
        return fetchPage(url).text();
    }

    public record Page(String title, String url, String text) { }

    public Page fetchPage(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .followRedirects(true)
                    .get();
            return new Page(doc.title(), doc.location(), extractText(doc));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch doc page: " + url, e);
        }
    }

    /** 从 HTML 文档抽取正文纯文本（去掉脚本/样式，优先 main/article 区域）。 */
    String extractText(Document doc) {
        doc.select("script, style, noscript, iframe, svg").remove();
        Element content = doc.selectFirst("main, article, .markdown-body");
        if (content == null) {
            content = doc.body();
        }
        return content.text().replaceAll("\\s+", " ").trim();
    }
}
