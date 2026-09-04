package com.techstack.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * 验证 HTML 正文抽取：去掉脚本/样式，优先取 main/article。
 */
class DocsFetcherTest {

    private final DocsFetcher fetcher = new DocsFetcher();

    @Test
    void extractsMainContentAndStripsScripts() {
        String html = """
                <html><head><script>alert('x')</script><style>.a{}</style></head>
                <body><nav>Home</nav><main><h1>Getting Started</h1><p>Hello   world</p></main></body></html>
                """;
        Document doc = Jsoup.parse(html);

        String text = fetcher.extractText(doc);

        assertThat(text).contains("Getting Started");
        assertThat(text).contains("Hello world");
        assertThat(text).doesNotContain("alert");
        assertThat(text).doesNotContain("Home");
    }
}
