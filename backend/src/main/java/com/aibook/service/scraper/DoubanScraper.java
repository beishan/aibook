package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String DOUBAN_API_URL = "https://book.douban.com/j/subject_suggest";
    private static final String DOUBAN_BOOK_URL = "https://book.douban.com/subject/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public DoubanScraper() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();

        // 设置请求头模拟真实浏览器，避免被豆瓣拦截
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("User-Agent", USER_AGENT);
            request.getHeaders().set("Referer", "https://book.douban.com/");
            request.getHeaders().set("Accept", "application/json, text/plain, */*");
            request.getHeaders().set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            request.getHeaders().set("Accept-Encoding", "gzip, deflate, br");
            request.getHeaders().set("Connection", "keep-alive");
            request.getHeaders().set("Host", "book.douban.com");
            return execution.execute(request, body);
        });
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
            String url = UriComponentsBuilder
                    .fromHttpUrl(DOUBAN_API_URL)
                    .queryParam("q", isbn)
                    .queryParam("type", "isbn")
                    .toUriString();

            log.info("查询豆瓣 ISBN: {}", isbn);
            String response = restTemplate.getForObject(url, String.class);

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
     * 通过书名搜索 - 使用 Jsoup 绕过豆瓣反爬检测
     */
    public BookMetadata searchByTitle(String title) {
        String searchTitle = MetadataScraper.cleanSearchTitle(title);
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(DOUBAN_API_URL)
                    .queryParam("q", searchTitle)
                    .queryParam("type", "book")
                    .toUriString();

            log.info("搜索豆瓣: {} (原始: {})", searchTitle, title);

            // 使用 Jsoup 发送请求，绕过 RestTemplate 的反爬检测
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .referrer("https://book.douban.com/")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(10000)
                    .ignoreContentType(true)
                    .get();

            String response = doc.body().text();
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode bookNode = root.get(0);
                String doubanId = bookNode.get("id").asText();
                log.info("豆瓣找到: {} (ID: {})", searchTitle, doubanId);
                return getBookDetail(doubanId);
            }

            log.warn("豆瓣未找到: {}", searchTitle);
            return null;
        } catch (Exception e) {
            log.error("豆瓣搜索失败: {}", searchTitle, e);
            return null;
        }
    }

    /**
     * 获取书籍详情（解析 HTML 页面）
     */
    private BookMetadata getBookDetail(String doubanId) {
        try {
            String bookUrl = DOUBAN_BOOK_URL + doubanId + "/";
            log.info("获取豆瓣书籍详情: {}", bookUrl);

            Document doc = Jsoup.connect(bookUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://book.douban.com/")
                    .timeout(10000)
                    .get();

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
                String infoText = infoElement.text();

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

                // 解析页数
                String pages = extractInfoField(infoElement, "页数");
                // 可以存储到自定义字段

                // 解析定价
                String price = extractInfoField(infoElement, "定价");
                // 可以存储到自定义字段

                // 解析 ISBN
                String isbn = extractInfoField(infoElement, "ISBN");
                if (isbn != null) {
                    builder.isbn(isbn);
                }

                // 解析装帧
                String binding = extractInfoField(infoElement, "装帧");
                // 可以存储到自定义字段
            }

            // 评分
            Element ratingElement = doc.selectFirst("strong.rating_num");
            if (ratingElement != null) {
                try {
                    double rating = Double.parseDouble(ratingElement.text().trim());
                    builder.rating(rating);
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
                // 获取所有段落
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

            // 作者简介
            Element authorIntroElement = doc.selectFirst("a[rel='nofollow'] + .intro");
            // 可以存储到自定义字段

            // 标签
            Elements tagElements = doc.select("#db-tags-section .tag a");
            if (!tagElements.isEmpty()) {
                List<String> tags = new ArrayList<>();
                for (Element tag : tagElements) {
                    tags.add(tag.text().trim());
                }
                builder.tags(tags.toArray(new String[0]));
            }

            // 语言（根据内容判断）
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
            // 豆瓣的 info 区域格式：<span>作者</span> : <a>作者名</a> 或 <span>作者</span> : 作者名
            String html = infoElement.html();

            // 使用正则表达式提取
            Pattern pattern = Pattern.compile(fieldName + ":</span>\\s*(.*?)(?:<br|</div|$)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String value = matcher.group(1);
                // 清理 HTML 标签
                value = Jsoup.parse(value).text().trim();
                // 移除开头的冒号和空号
                value = value.replaceAll("^[:\\s]+", "");
                return value.isEmpty() ? null : value;
            }

            return null;
        } catch (Exception e) {
            log.debug("提取字段 {} 失败", fieldName, e);
            return null;
        }
    }
}
