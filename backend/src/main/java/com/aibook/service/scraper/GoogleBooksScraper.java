package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.aibook.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google Books 刮削器
 * 需要 API Key，数据全面
 * 文档：https://developers.google.com/books
 */
@Component
@org.springframework.core.annotation.Order(2)
@Slf4j
public class GoogleBooksScraper implements MetadataScraper {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigService configService;

    @Value("${scraper.google-books.api-key:}")
    private String fallbackApiKey;

    public GoogleBooksScraper(SystemConfigService configService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.configService = configService;
    }

    /**
     * 获取 API Key（优先从数据库读取，其次从配置文件）
     */
    private String getApiKey() {
        String dbKey = configService.getConfig("scraper.google.api-key", null);
        if (dbKey != null && !dbKey.isBlank()) {
            return dbKey;
        }
        return fallbackApiKey;
    }

    @Override
    public String getName() {
        return "Google Books";
    }

    @Override
    public String getConfigKey() {
        return "google";
    }

    @Override
    public boolean supports(Book book) {
        // 有 API Key 时支持所有书籍
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isBlank();
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
                    .fromHttpUrl("https://www.googleapis.com/books/v1/volumes")
                    .queryParam("q", "isbn:" + isbn)
                    .queryParam("key", getApiKey())
                    .toUriString();

            log.info("查询 Google Books ISBN: {}", isbn);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.get("items");

            if (items == null || items.isEmpty()) {
                log.warn("Google Books 未找到 ISBN: {}", isbn);
                return null;
            }

            return parseVolumeInfo(items.get(0).get("volumeInfo"));
        } catch (Exception e) {
            log.error("Google Books ISBN 查询失败: {}", isbn, e);
            return null;
        }
    }

    /**
     * 通过书名搜索
     */
    public BookMetadata searchByTitle(String title) {
        String searchTitle = MetadataScraper.cleanSearchTitle(title);
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://www.googleapis.com/books/v1/volumes")
                    .queryParam("q", "intitle:" + searchTitle)
                    .queryParam("maxResults", 1)
                    .queryParam("key", getApiKey())
                    .toUriString();

            log.info("搜索 Google Books: {} (原始: {})", searchTitle, title);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.get("items");

            if (items == null || items.isEmpty()) {
                log.warn("Google Books 未找到: {}", title);
                return null;
            }

            return parseVolumeInfo(items.get(0).get("volumeInfo"));
        } catch (Exception e) {
            log.error("Google Books 搜索失败: {}", title, e);
            return null;
        }
    }

    /**
     * 解析 volumeInfo
     */
    private BookMetadata parseVolumeInfo(JsonNode node) {
        try {
            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // 标题
            if (node.has("title")) {
                builder.title(node.get("title").asText());
            }

            // 副标题
            if (node.has("subtitle")) {
                String subtitle = node.get("subtitle").asText();
                // 可以追加到标题
            }

            // 作者
            if (node.has("authors")) {
                JsonNode authors = node.get("authors");
                if (authors.isArray() && !authors.isEmpty()) {
                    StringBuilder authorBuilder = new StringBuilder();
                    for (JsonNode author : authors) {
                        if (authorBuilder.length() > 0) {
                            authorBuilder.append(", ");
                        }
                        authorBuilder.append(author.asText());
                    }
                    builder.author(authorBuilder.toString());
                }
            }

            // 出版社
            if (node.has("publisher")) {
                builder.publisher(node.get("publisher").asText());
            }

            // 出版日期
            if (node.has("publishedDate")) {
                builder.publishDate(node.get("publishedDate").asText());
            }

            // 描述
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }

            // ISBN
            if (node.has("industryIdentifiers")) {
                JsonNode identifiers = node.get("industryIdentifiers");
                for (JsonNode identifier : identifiers) {
                    String type = identifier.get("type").asText();
                    if ("ISBN_13".equals(type) || "ISBN_10".equals(type)) {
                        builder.isbn(identifier.get("identifier").asText());
                        break;
                    }
                }
            }

            // 页数
            if (node.has("pageCount")) {
                // 可以存储到自定义字段
            }

            // 语言
            if (node.has("language")) {
                builder.language(node.get("language").asText());
            }

            // 封面
            if (node.has("imageLinks")) {
                JsonNode images = node.get("imageLinks");
                if (images.has("thumbnail")) {
                    builder.coverUrl(images.get("thumbnail").asText());
                } else if (images.has("smallThumbnail")) {
                    builder.coverUrl(images.get("smallThumbnail").asText());
                }
            }

            // 评分（Google Books 不提供平均评分）
            if (node.has("averageRating")) {
                builder.rating(node.get("averageRating").asDouble());
            }

            // 分类/标签
            if (node.has("categories")) {
                JsonNode categories = node.get("categories");
                if (categories.isArray() && !categories.isEmpty()) {
                    String[] tags = new String[categories.size()];
                    for (int i = 0; i < categories.size(); i++) {
                        tags[i] = categories.get(i).asText();
                    }
                    builder.tags(tags);
                }
            }

            return builder.build();
        } catch (Exception e) {
            log.error("解析 Google Books 数据失败", e);
            return null;
        }
    }
}
