package com.aibook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 元数据管理服务
 * 支持从豆瓣读书、Open Library 等来源获取书籍元数据
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${metadata.douban.app-key:}")
    private String doubanAppKey;

    @Value("${metadata.open-library.enabled:true}")
    private boolean openLibraryEnabled;

    /**
     * 通过 ISBN 查询书籍元数据
     */
    public Map<String, Object> searchByIsbn(String isbn) {
        Map<String, Object> metadata = new HashMap<>();

        // 尝试从豆瓣获取
        Map<String, Object> doubanData = searchDoubanByIsbn(isbn);
        if (doubanData != null && !doubanData.isEmpty()) {
            metadata.putAll(doubanData);
            metadata.put("source", "douban");
            return metadata;
        }

        // 尝试从 Open Library 获取
        Map<String, Object> openLibData = searchOpenLibraryByIsbn(isbn);
        if (openLibData != null && !openLibData.isEmpty()) {
            metadata.putAll(openLibData);
            metadata.put("source", "openlibrary");
            return metadata;
        }

        return metadata;
    }

    /**
     * 通过书名搜索书籍
     */
    public Map<String, Object> searchByTitle(String title, String author) {
        Map<String, Object> metadata = new HashMap<>();

        // 尝试从豆瓣搜索
        Map<String, Object> doubanData = searchDoubanByTitle(title, author);
        if (doubanData != null && !doubanData.isEmpty()) {
            metadata.putAll(doubanData);
            metadata.put("source", "douban");
            return metadata;
        }

        // 尝试从 Open Library 搜索
        Map<String, Object> openLibData = searchOpenLibraryByTitle(title, author);
        if (openLibData != null && !openLibData.isEmpty()) {
            metadata.putAll(openLibData);
            metadata.put("source", "openlibrary");
            return metadata;
        }

        return metadata;
    }

    /**
     * 从豆瓣读书通过 ISBN 查询
     */
    private Map<String, Object> searchDoubanByIsbn(String isbn) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.douban.com/v2/book/isbn/")
                    .pathSegment(isbn)
                    .build()
                    .toUriString();

            HttpHeaders headers = createDoubanHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseDoubanResponse(response.getBody());
            }
        } catch (Exception e) {
            log.warn("豆瓣 API 查询失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从豆瓣读书通过书名搜索
     */
    private Map<String, Object> searchDoubanByTitle(String title, String author) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.douban.com/v2/book/search")
                    .queryParam("q", title)
                    .queryParam("count", 1)
                    .build()
                    .toUriString();

            HttpHeaders headers = createDoubanHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode books = root.get("books");
                if (books != null && books.isArray() && books.size() > 0) {
                    return parseDoubanResponse(books.get(0).toString());
                }
            }
        } catch (Exception e) {
            log.warn("豆瓣搜索失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 解析豆瓣响应
     */
    private Map<String, Object> parseDoubanResponse(String json) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            JsonNode node = objectMapper.readTree(json);

            metadata.put("title", getField(node, "title"));
            metadata.put("author", getField(node, "author"));
            metadata.put("isbn", getField(node, "isbn13"));
            metadata.put("publisher", getField(node, "publisher"));
            metadata.put("publishDate", getField(node, "pubdate"));
            metadata.put("description", getField(node, "summary"));
            metadata.put("coverUrl", getField(node, "image"));
            metadata.put("pageCount", getField(node, "pages"));
            metadata.put("price", getField(node, "price"));

            // 提取作者数组
            JsonNode authorNode = node.get("author");
            if (authorNode != null && authorNode.isArray()) {
                StringBuilder authorStr = new StringBuilder();
                for (JsonNode a : authorNode) {
                    if (authorStr.length() > 0) authorStr.append(", ");
                    authorStr.append(a.asText());
                }
                metadata.put("author", authorStr.toString());
            }

            return metadata;
        } catch (Exception e) {
            log.error("解析豆瓣响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Open Library 通过 ISBN 查询
     */
    private Map<String, Object> searchOpenLibraryByIsbn(String isbn) {
        if (!openLibraryEnabled) return null;

        try {
            String url = String.format("https://openlibrary.org/api/books?bibkeys=ISBN:%s&format=json&jscmd=data", isbn);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Aibook/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode bookNode = root.get("ISBN:" + isbn);
                if (bookNode != null) {
                    return parseOpenLibraryResponse(bookNode);
                }
            }
        } catch (Exception e) {
            log.warn("Open Library 查询失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 Open Library 通过书名搜索
     */
    private Map<String, Object> searchOpenLibraryByTitle(String title, String author) {
        if (!openLibraryEnabled) return null;

        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://openlibrary.org/search.json")
                    .queryParam("title", title)
                    .queryParam("limit", 1)
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Aibook/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode docs = root.get("docs");
                if (docs != null && docs.isArray() && docs.size() > 0) {
                    JsonNode bookNode = docs.get(0);
                    Map<String, Object> metadata = new HashMap<>();

                    metadata.put("title", getField(bookNode, "title"));
                    metadata.put("author", getField(bookNode, "author_name"));
                    metadata.put("publisher", getField(bookNode, "publisher"));
                    metadata.put("publishDate", getField(bookNode, "first_publish_year"));
                    metadata.put("isbn", getField(bookNode, "isbn"));

                    // 获取封面
                    JsonNode cover = bookNode.get("cover_i");
                    if (cover != null && !cover.isNull()) {
                        metadata.put("coverUrl",
                            String.format("https://covers.openlibrary.org/b/id/%s-L.jpg", cover.asText()));
                    }

                    return metadata;
                }
            }
        } catch (Exception e) {
            log.warn("Open Library 搜索失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 解析 Open Library 响应
     */
    private Map<String, Object> parseOpenLibraryResponse(JsonNode node) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("title", getField(node, "title"));

        // 作者
        JsonNode authors = node.get("authors");
        if (authors != null && authors.isArray()) {
            StringBuilder authorStr = new StringBuilder();
            for (JsonNode author : authors) {
                JsonNode name = author.get("name");
                if (name != null) {
                    if (authorStr.length() > 0) authorStr.append(", ");
                    authorStr.append(name.asText());
                }
            }
            metadata.put("author", authorStr.toString());
        }

        // 出版社
        JsonNode publishers = node.get("publishers");
        if (publishers != null && publishers.isArray() && publishers.size() > 0) {
            metadata.put("publisher", publishers.get(0).get("name").asText());
        }

        // 出版日期
        metadata.put("publishDate", getField(node, "publish_date"));

        // 封面
        JsonNode cover = node.get("cover");
        if (cover != null) {
            metadata.put("coverUrl", getField(cover, "medium"));
        }

        // 页数
        metadata.put("pageCount", getField(node, "number_of_pages"));

        return metadata;
    }

    /**
     * 创建豆瓣请求头
     */
    private HttpHeaders createDoubanHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Aibook/1.0");
        if (doubanAppKey != null && !doubanAppKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + doubanAppKey);
        }
        return headers;
    }

    /**
     * 安全获取字段值
     */
    private String getField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field != null && !field.isNull()) {
            if (field.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : field) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(item.asText());
                }
                return sb.toString();
            }
            return field.asText();
        }
        return null;
    }
}
