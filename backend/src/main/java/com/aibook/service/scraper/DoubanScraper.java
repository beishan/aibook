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
 * 豆瓣读书刮削器
 * 中文书籍信息最丰富的数据源
 */
@Component
@org.springframework.core.annotation.Order(1)
@Slf4j
public class DoubanScraper implements MetadataScraper {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final String DOUBAN_SEARCH_URL = "https://www.douban.com/search";
    private static final String DOUBAN_BOOK_URL = "https://book.douban.com/subject/";
    private static final String DOUBAN_SUGGEST_URL = "https://book.douban.com/j/subject_suggest";

    // 多个 User-Agent 轮换
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    public DoubanScraper() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "豆瓣读书";
    }

    @Override
    public String getConfigKey() {
        return "douban";
    }

    @Override
    public boolean supports(Book book) {
        // 中文书籍优先使用豆瓣
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

            String url = DOUBAN_SUGGEST_URL + "?q=" + isbn + "&type=isbn";
            log.info("查询豆瓣 ISBN: {}", isbn);

            String response = makeRequest(url);
            if (response == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode bookNode = root.get(0);
                String doubanId = bookNode.get("id").asText();
                return getBookDetail(doubanId);
            }

            log.warn("豆瓣未找到 ISBN: {}", isbn);
            return null;
        } catch (Exception e) {
            log.error("豆瓣 ISBN 查询失败: {}", isbn, e);
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

            // 尝试使用豆瓣搜索页面
            String searchUrl = DOUBAN_SEARCH_URL + "?cat=1001&q=" + searchTitle;
            log.info("搜索豆瓣: {} (原始: {})", searchTitle, title);

            String response = makeRequest(searchUrl);
            if (response == null) {
                // 尝试备用 API
                return searchByTitleFallback(searchTitle, title);
            }

            // 解析搜索结果页面
            Document doc = Jsoup.parse(response);

            // 查找书籍链接
            Elements bookLinks = doc.select("a[href*='book.douban.com/subject/']");
            if (!bookLinks.isEmpty()) {
                String href = bookLinks.first().attr("href");
                Pattern pattern = Pattern.compile("subject/(\\d+)");
                Matcher matcher = pattern.matcher(href);
                if (matcher.find()) {
                    String doubanId = matcher.group(1);
                    log.info("豆瓣找到: {} (ID: {})", searchTitle, doubanId);
                    return getBookDetail(doubanId);
                }
            }

            log.warn("豆瓣未找到: {}", searchTitle);
            return null;
        } catch (Exception e) {
            log.error("豆瓣搜索失败: {}", searchTitle, e);
            // 尝试备用方法
            return searchByTitleFallback(searchTitle, title);
        }
    }

    /**
     * 备用搜索方法 - 使用 suggest API
     */
    private BookMetadata searchByTitleFallback(String searchTitle, String originalTitle) {
        try {
            addRandomDelay();

            String url = DOUBAN_SUGGEST_URL + "?q=" + searchTitle + "&type=book";
            log.info("豆瓣备用搜索: {}", searchTitle);

            String response = makeRequest(url);
            if (response == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode bookNode = root.get(0);
                String doubanId = bookNode.get("id").asText();
                log.info("豆瓣备用找到: {} (ID: {})", searchTitle, doubanId);
                return getBookDetail(doubanId);
            }

            return null;
        } catch (Exception e) {
            log.error("豆瓣备用搜索失败: {}", searchTitle, e);
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
                    .referrer("https://book.douban.com/")
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
                log.warn("豆瓣返回 403，可能被反爬拦截: {}", url);
                return null;
            } else {
                log.warn("豆瓣返回状态码 {}: {}", response.statusCode(), url);
                return null;
            }
        } catch (Exception e) {
            log.error("请求豆瓣失败: {}", url, e);
            return null;
        }
    }

    /**
     * 获取书籍详情（解析 HTML 页面）
     */
    private BookMetadata getBookDetail(String doubanId) {
        try {
            addRandomDelay();

            String bookUrl = DOUBAN_BOOK_URL + doubanId + "/";
            log.info("获取豆瓣书籍详情: {}", bookUrl);

            String response = makeRequest(bookUrl);
            if (response == null) {
                return null;
            }

            Document doc = Jsoup.parse(response);
            return parseBookPage(doc, doubanId);
        } catch (Exception e) {
            log.error("获取豆瓣书籍详情失败: {}", doubanId, e);
            return null;
        }
    }

    /**
     * 解析豆瓣书籍页面
     */
    private BookMetadata parseBookPage(Document doc, String doubanId) {
        try {
            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // 标题
            Element titleElement = doc.selectFirst("h1 span");
            if (titleElement != null) {
                builder.title(titleElement.text().trim());
            }

            // 作者信息（在 #info div 中）
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                // 解析作者
                String author = extractInfoField(infoElement, "作者");
                if (author != null) {
                    builder.author(author);
                }

                // 解析出版社
                String publisher = extractInfoField(infoElement, "出版社");
                if (publisher != null) {
                    builder.publisher(publisher);
                }

                // 解析出版年
                String publishDate = extractInfoField(infoElement, "出版年");
                if (publishDate != null) {
                    builder.publishDate(publishDate);
                }

                // 解析 ISBN
                String isbn = extractInfoField(infoElement, "ISBN");
                if (isbn != null) {
                    builder.isbn(isbn);
                }
            }

            // 评分
            Element ratingElement = doc.selectFirst("strong.rating_num");
            if (ratingElement != null) {
                try {
                    String ratingText = ratingElement.text().trim();
                    if (!ratingText.isEmpty()) {
                        double rating = Double.parseDouble(ratingText);
                        builder.rating(rating);
                    }
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }

            // 封面图片
            Element coverElement = doc.selectFirst("#mainpic img");
            if (coverElement != null) {
                String coverUrl = coverElement.attr("src");
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    builder.coverUrl(coverUrl);
                }
            }

            // 内容简介
            Element summaryElement = doc.selectFirst("#link-report .intro");
            if (summaryElement != null) {
                Elements paragraphs = summaryElement.select("p");
                StringBuilder summary = new StringBuilder();
                for (Element p : paragraphs) {
                    if (summary.length() > 0) {
                        summary.append("\n");
                    }
                    summary.append(p.text().trim());
                }
                if (summary.length() > 0) {
                    builder.description(summary.toString());
                }
            }

            // 标签
            Elements tagElements = doc.select("#db-tags-section .tag a");
            if (!tagElements.isEmpty()) {
                List<String> tags = new ArrayList<>();
                for (Element tag : tagElements) {
                    tags.add(tag.text().trim());
                }
                builder.tags(tags.toArray(new String[0]));
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
            log.error("解析豆瓣页面失败", e);
            return null;
        }
    }

    /**
     * 从 info 区域提取字段值
     */
    private String extractInfoField(Element infoElement, String fieldName) {
        try {
            String html = infoElement.html();
            Pattern pattern = Pattern.compile(fieldName + ":</span>\\s*(.*?)(?:<br|</div|$)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String value = matcher.group(1);
                value = Jsoup.parse(value).text().trim();
                value = value.replaceAll("^[:\\s]+", "");
                return value.isEmpty() ? null : value;
            }

            return null;
        } catch (Exception e) {
            log.debug("提取字段 {} 失败", fieldName, e);
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
