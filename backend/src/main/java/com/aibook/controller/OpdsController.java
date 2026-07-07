package com.aibook.controller;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.Opds2Service;
import com.aibook.service.OpdsService;
import com.aibook.service.UserService;
import com.aibook.util.MimeTypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * OPDS 控制器
 * 实现 OPDS 1.2 和 OPDS 2.0 协议的电子书目录服务
 */
@RestController
@RequestMapping("/opds")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class OpdsController {

    private static final String OPDS_ATOM_TYPE = "application/atom+xml;profile=opds-catalog";
    private static final String OPDS_JSON_TYPE = "application/opds+json";
    private static final String OPENSEARCH_TYPE = "application/opensearchdescription+xml";

    private final OpdsService opdsService;
    private final Opds2Service opds2Service;
    private final BookRepository bookRepository;
    private final UserService userService;

    // ==================== OPDS 1.2 端点 ====================

    /**
     * 根目录
     */
    @GetMapping({"", "/"})
    public ResponseEntity<String> getRootCatalog(Authentication authentication) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.getRootCatalog(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * 所有书籍（分页）
     */
    @GetMapping(value = "/books")
    public ResponseEntity<String> getBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.getBooksCatalog(user, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * 按格式获取书籍（分页）
     */
    @GetMapping(value = "/formats/{format}")
    public ResponseEntity<String> getBooksByFormat(
            Authentication authentication,
            @PathVariable String format,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.getBooksByFormat(user, format, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * 格式列表
     */
    @GetMapping(value = "/formats")
    public ResponseEntity<String> getFormats(Authentication authentication) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.getFormatsCatalog(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * 收藏书籍（分页）
     */
    @GetMapping(value = "/favorites")
    public ResponseEntity<String> getFavorites(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.getFavoriteBooks(user, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * 正在阅读的书籍（分页）
     */
    @GetMapping(value = "/reading")
    public ResponseEntity<String> getReading(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.getReadingBooks(user, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * 搜索书籍（分页）
     */
    @GetMapping(value = "/search")
    public ResponseEntity<String> search(
            Authentication authentication,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        String catalog = opdsService.searchBooks(user, query, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_ATOM_TYPE)
                .body(catalog);
    }

    /**
     * OpenSearch 描述
     */
    @GetMapping(value = "/search.xml")
    public ResponseEntity<String> getSearchDescription(Authentication authentication) {
        getUserFromAuth(authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPENSEARCH_TYPE)
                .body(opdsService.getSearchDescription());
    }

    /**
     * 下载书籍
     */
    @GetMapping("/books/{id}/download")
    public ResponseEntity<Resource> downloadBook(
            Authentication authentication,
            @PathVariable Long id) throws IOException {

        User user = getUserFromAuth(authentication);

        Book book = bookRepository.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElse(null);

        if (book == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(book.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        File file = filePath.toFile();
        FileSystemResource resource = new FileSystemResource(file);

        String contentType = MimeTypeUtil.getContentType(book.getFormat());
        String extension = book.getFormat().toLowerCase();
        String filename = book.getTitle() + "." + extension;
        String fallbackFilename = "book-" + book.getId() + "." + extension;
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fallbackFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("Content-Length", String.valueOf(file.length()))
                .body(resource);
    }

    // ==================== OPDS 2.0 端点 ====================

    /**
     * OPDS 2.0 根目录
     */
    @GetMapping(value = "/v2")
    public ResponseEntity<Map<String, Object>> getV2RootCatalog(Authentication authentication) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.getRootCatalog(user));
    }

    /**
     * OPDS 2.0 格式列表
     */
    @GetMapping(value = "/v2/formats")
    public ResponseEntity<Map<String, Object>> getV2Formats(Authentication authentication) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.getFormatsCatalog(user));
    }

    /**
     * OPDS 2.0 所有书籍
     */
    @GetMapping(value = "/v2/books")
    public ResponseEntity<Map<String, Object>> getV2Books(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.getBooksCatalog(user, page));
    }

    /**
     * OPDS 2.0 按格式获取书籍
     */
    @GetMapping(value = "/v2/formats/{format}")
    public ResponseEntity<Map<String, Object>> getV2BooksByFormat(
            Authentication authentication,
            @PathVariable String format,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.getBooksByFormat(user, format, page));
    }

    /**
     * OPDS 2.0 收藏书籍
     */
    @GetMapping(value = "/v2/favorites")
    public ResponseEntity<Map<String, Object>> getV2Favorites(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.getFavoriteBooks(user, page));
    }

    /**
     * OPDS 2.0 正在阅读的书籍
     */
    @GetMapping(value = "/v2/reading")
    public ResponseEntity<Map<String, Object>> getV2Reading(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.getReadingBooks(user, page));
    }

    /**
     * OPDS 2.0 搜索书籍
     */
    @GetMapping(value = "/v2/search")
    public ResponseEntity<Map<String, Object>> v2Search(
            Authentication authentication,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page) {
        User user = getUserFromAuth(authentication);
        return opdsJson(opds2Service.searchBooks(user, query, page));
    }

    private ResponseEntity<Map<String, Object>> opdsJson(Map<String, Object> body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, OPDS_JSON_TYPE)
                .body(body);
    }

    /**
     * 获取用户
     */
    private User getUserFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }
}
