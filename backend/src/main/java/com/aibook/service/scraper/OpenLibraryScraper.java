package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * Open Library 刮削器
 * 免费开放的图书馆 API，适合英文书籍
 * 文档：https://openlibrary.org/developers/api
 */
@Component
@org.springframework.core.annotation.Order(3)
@Slf4j
public class OpenLibraryScraper implements MetadataScraper {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenLibraryScraper() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "Open Library";
    }

    @Override
    public String getConfigKey() {
        return "openlibrary";
    }

    @Override
    public boolean supports(Book book) {
        // 支持所有书籍，优先使用 ISBN
        return true;
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
                    .fromHttpUrl("https://openlibrary.org/api/books")
                    .queryParam("bibkeys", "ISBN:" + isbn)
                    .queryParam("format", "json")
                    .queryParam("jscmd", "data")
                    .toUriString();

            log.info("查询 Open Library ISBN: {}", isbn);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode bookNode = root.get("ISBN:" + isbn);

            if (bookNode == null) {
                log.warn("Open Library 未找到 ISBN: {}", isbn);
                return null;
            }

            return parseBookData(bookNode);
        } catch (Exception e) {
            log.error("Open Library ISBN 查询失败: {}", isbn, e);
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
                    .fromHttpUrl("https://openlibrary.org/search.json")
                    .queryParam("title", searchTitle)
                    .queryParam("limit", 1)
                    .toUriString();

            log.info("搜索 Open Library: {} (原始: {})", searchTitle, title);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode docs = root.get("docs");

            if (docs == null || docs.isEmpty()) {
                log.warn("Open Library 未找到: {}", title);
                return null;
            }

            JsonNode firstDoc = docs.get(0);
            return parseSearchResult(firstDoc);
        } catch (Exception e) {
            log.error("Open Library 搜索失败: {}", title, e);
            return null;
        }
    }

    /**
     * 解析书籍数据（ISBN 查询结果）
     */
    private BookMetadata parseBookData(JsonNode node) {
        try {
            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // 标题
            if (node.has("title")) {
                builder.title(node.get("title").asText());
            }

            // 作者
            if (node.has("authors")) {
                JsonNode authors = node.get("authors");
                if (authors.isArray() && !authors.isEmpty()) {
                    StringBuilder authorBuilder = new StringBuilder();
                    for (JsonNode author : authors) {
                        if (author.has("name")) {
                            if (authorBuilder.length() > 0) {
                                authorBuilder.append(", ");
                            }
                            authorBuilder.append(author.get("name").asText());
                        }
                    }
                    builder.author(authorBuilder.toString());
                }
            }

            // 出版社
            if (node.has("publishers")) {
                JsonNode publishers = node.get("publishers");
                if (publishers.isArray() && !publishers.isEmpty()) {
                    builder.publisher(publishers.get(0).asText());
                }
            }

            // 出版日期
            if (node.has("publish_date")) {
                builder.publishDate(node.get("publish_date").asText());
            }

            // 封面
            if (node.has("cover")) {
                JsonNode cover = node.get("cover");
                if (cover.has("large")) {
                    builder.coverUrl(cover.get("large").asText());
                } else if (cover.has("medium")) {
                    builder.coverUrl(cover.get("medium").asText());
                }
            }

            // 页数（可作为描述补充）
            if (node.has("number_of_pages")) {
                builder.description("页数: " + node.get("number_of_pages").asInt());
            }

            return builder.build();
        } catch (Exception e) {
            log.error("解析 Open Library 数据失败", e);
            return null;
        }
    }

    /**
     * 解析搜索结果
     */
    private BookMetadata parseSearchResult(JsonNode node) {
        try {
            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // 标题
            if (node.has("title")) {
                builder.title(node.get("title").asText());
            }

            // 作者
            if (node.has("author_name")) {
                JsonNode authors = node.get("author_name");
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

            // ISBN
            if (node.has("isbn")) {
                JsonNode isbns = node.get("isbn");
                if (isbns.isArray() && !isbns.isEmpty()) {
                    builder.isbn(isbns.get(0).asText());
                }
            }

            // 出版社
            if (node.has("publisher")) {
                JsonNode publishers = node.get("publisher");
                if (publishers.isArray() && !publishers.isEmpty()) {
                    builder.publisher(publishers.get(0).asText());
                }
            }

            // 出版日期
            if (node.has("first_publish_year")) {
                builder.publishDate(String.valueOf(node.get("first_publish_year").asInt()));
            }

            // 语言
            if (node.has("language")) {
                JsonNode languages = node.get("language");
                if (languages.isArray() && !languages.isEmpty()) {
                    builder.language(languages.get(0).asText());
                }
            }

            // 封面
            if (node.has("cover_i")) {
                String coverId = node.get("cover_i").asText();
                builder.coverUrl("https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("解析 Open Library 搜索结果失败", e);
            return null;
        }
    }
}
