package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 京东读书刮削器
 * 中文书籍信息丰富的数据源
 */
@Component
@org.springframework.core.annotation.Order(2)
@Slf4j
public class JdBookScraper implements MetadataScraper {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final String JD_SEARCH_URL = "https://search.jd.com/Search";
    private static final String JD_BOOK_URL = "https://item.jd.com/";

    // 多个 User-Agent 轮换
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    public JdBookScraper() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "京东读书";
    }

    @Override
    public String getConfigKey() {
        return "jd";
    }

    @Override
    public boolean supports(Book book) {
        // 中文书籍优先使用京东
        String title = book.getTitle();
        if (title == null) return false;

        // 简单判断是否包含中文字符
        return title.matches(".*[\\u4e00-\\u9fa5].*");
    }

    @Override
    public BookMetadata scrape(Book book) {
        // 尝试通过 ISBN 查询
        if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
            BookMetadata metadata = scrapeByIsbn(book.getIsbn());
            if (metadata != null) {
                return metadata;
            }
        }

        // 通过书名搜索
        return searchByTitle(book.getTitle());
    }

    @Override
    public BookMetadata scrapeByIsbn(String isbn) {
        try {
            // 添加随机延迟，避免请求过快
            addRandomDelay();

            String searchUrl = JD_SEARCH_URL + "?keyword=" + isbn + "&enc=utf-8";
            log.info("查询京东 ISBN: {}", isbn);

            String response = makeRequest(searchUrl);
            if (response == null) {
                return null;
            }

            // 解析搜索结果页面
            Document doc = Jsoup.parse(response);

            // 查找书籍链接
            Elements bookLinks = doc.select("a[href*='item.jd.com/']");
            if (!bookLinks.isEmpty()) {
                String href = bookLinks.first().attr("href");
                Pattern pattern = Pattern.compile("item.jd.com/(\\d+)");
                Matcher matcher = pattern.matcher(href);
                if (matcher.find()) {
                    String jdId = matcher.group(1);
                    log.info("京东找到: {} (ID: {})", isbn, jdId);
                    return getBookDetail(jdId);
                }
            }

            log.warn("京东未找到 ISBN: {}", isbn);
            return null;
        } catch (Exception e) {
            log.error("京东 ISBN 查询失败: {}", isbn, e);
            return null;
        }
    }

    /**
     * 通过书名搜索
     */
    public BookMetadata searchByTitle(String title) {
        String searchTitle = MetadataScraper.cleanSearchTitle(title);
        try {
            // 添加随机延迟
            addRandomDelay();

            String searchUrl = JD_SEARCH_URL + "?keyword=" + searchTitle + "&enc=utf-8";
            log.info("搜索京东: {} (原始: {})", searchTitle, title);

            String response = makeRequest(searchUrl);
            if (response == null) {
                return null;
            }

            // 解析搜索结果页面
            Document doc = Jsoup.parse(response);

            // 查找书籍链接
            Elements bookLinks = doc.select("a[href*='item.jd.com/']");
            if (!bookLinks.isEmpty()) {
                String href = bookLinks.first().attr("href");
                Pattern pattern = Pattern.compile("item.jd.com/(\\d+)");
                Matcher matcher = pattern.matcher(href);
                if (matcher.find()) {
                    String jdId = matcher.group(1);
                    log.info("京东找到: {} (ID: {})", searchTitle, jdId);
                    return getBookDetail(jdId);
                }
            }

            log.warn("京东未找到: {}", searchTitle);
            return null;
        } catch (Exception e) {
            log.error("京东搜索失败: {}", searchTitle, e);
            return null;
        }
    }

    /**
     * 发送 HTTP 请求
     */
    private String makeRequest(String url) {
        try {
            String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer("https://www.jd.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .timeout(15000)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 403) {
                log.warn("京东返回 403，可能被反爬拦截: {}", url);
                return null;
            } else {
                log.warn("京东返回状态码 {}: {}", response.statusCode(), url);
                return null;
            }
        } catch (Exception e) {
            log.error("请求京东失败: {}", url, e);
            return null;
        }
    }

    /**
     * 获取书籍详情（解析 HTML 页面）
     */
    private BookMetadata getBookDetail(String jdId) {
        try {
            addRandomDelay();

            String bookUrl = JD_BOOK_URL + jdId + ".html";
            log.info("获取京东书籍详情: {}", bookUrl);

            String response = makeRequest(bookUrl);
            if (response == null) {
                return null;
            }

            Document doc = Jsoup.parse(response);
            return parseBookPage(doc, jdId);
        } catch (Exception e) {
            log.error("获取京东书籍详情失败: {}", jdId, e);
            return null;
        }
    }

    /**
     * 解析京东书籍页面
     */
    private BookMetadata parseBookPage(Document doc, String jdId) {
        try {
            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // 标题
            Element titleElement = doc.selectFirst(".sku-name");
            if (titleElement != null) {
                builder.title(titleElement.text().trim());
            }

            // 作者信息
            Element authorElement = doc.selectFirst("a[href*='author.jd.com']");
            if (authorElement != null) {
                builder.author(authorElement.text().trim());
            }

            // 出版社
            Element publisherElement = doc.selectFirst("a[href*='publisher.jd.com']");
            if (publisherElement != null) {
                builder.publisher(publisherElement.text().trim());
            }

            // 出版日期
            Element dateElement = doc.selectFirst(".publish-time");
            if (dateElement != null) {
                builder.publishDate(dateElement.text().trim());
            }

            // ISBN
            Element isbnElement = doc.selectFirst(".isbn");
            if (isbnElement != null) {
                builder.isbn(isbnElement.text().trim());
            }

            // 封面图片
            Element coverElement = doc.selectFirst("#spec-img");
            if (coverElement != null) {
                String coverUrl = coverElement.attr("src");
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    // 补全协议头
                    if (coverUrl.startsWith("//")) {
                        coverUrl = "https:" + coverUrl;
                    }
                    builder.coverUrl(coverUrl);
                }
            }

            // 内容简介
            Element summaryElement = doc.selectFirst("#detail .book-intro");
            if (summaryElement != null) {
                builder.description(summaryElement.text().trim());
            }

            // 语言
            String title = builder.build().getTitle();
            if (title != null && title.matches(".*[\\u4e00-\\u9fa5].*")) {
                builder.language("zh");
            } else {
                builder.language("en");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("解析京东页面失败", e);
            return null;
        }
    }

    /**
     * 添加随机延迟，避免请求过快
     */
    private void addRandomDelay() {
        try {
            // 随机延迟 1-3 秒
            int delay = 1000 + random.nextInt(2000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
