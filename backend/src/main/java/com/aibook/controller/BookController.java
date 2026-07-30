package com.aibook.controller;

import com.aibook.dto.BatchScrapeRequest;
import com.aibook.dto.BookCategoryRequest;
import com.aibook.dto.BookIdsRequest;
import com.aibook.dto.BookTagsRequest;
import com.aibook.dto.BookDTO;
import com.aibook.dto.BookTocItemDTO;
import com.aibook.dto.BookVersionRebuildTaskDTO;
import com.aibook.dto.ScrapeTaskDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.BookCoverService;
import com.aibook.service.BookParsingService;
import com.aibook.service.BookService;
import com.aibook.service.BookVersionRebuildTaskService;
import com.aibook.service.BookVersionService;
import com.aibook.service.TxtParserService;
import com.aibook.service.UserService;
import com.aibook.service.scraper.BatchScrapeTaskService;
import com.aibook.service.scraper.CoverDownloadService;
import com.aibook.service.scraper.MetadataScrapingService;
import com.aibook.util.MimeTypeUtil;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.context.request.WebRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 书籍控制器
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class BookController {

    private final BookService bookService;
    private final UserService userService;
    private final MetadataScrapingService metadataScrapingService;
    private final CoverDownloadService coverDownloadService;
    private final BatchScrapeTaskService batchScrapeTaskService;
    private final TxtParserService txtParserService;
    private final BookRepository bookRepository;
    private final BookParsingService bookParsingService;
    private final BookCoverService bookCoverService;
    private final BookVersionService bookVersionService;
    private final BookVersionRebuildTaskService bookVersionRebuildTaskService;

    /**
     * 获取书籍列表
     */
    @GetMapping
    public ResponseEntity<Page<BookDTO>> getBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeChildren,
            @RequestParam(required = false) Long tagId) {

        User user = userService.findByUsername(authentication.getName());

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<BookDTO> books;

        if (format != null && !format.isEmpty()) {
            books = bookService.getBooksByFormat(user, format, pageRequest);
        } else if (status != null && !status.isEmpty()) {
            Book.ReadingStatus readingStatus = Book.ReadingStatus.valueOf(status);
            books = bookService.getBooksByStatus(user, readingStatus, pageRequest);
        } else if (categoryId != null) {
            books = bookService.getBooksByCategory(
                    user, categoryId, includeChildren, pageRequest);
        } else if (tagId != null) {
            books = bookService.getBooksByTag(user, tagId, pageRequest);
        } else {
            books = bookService.getBooks(user, pageRequest);
        }

        return ResponseEntity.ok(books);
    }

    /**
     * 遍历当前用户书库，将历史独立记录中的同一本书聚合为多个版本。
     */
    @PostMapping("/versions/rebuild")
    public ResponseEntity<BookVersionRebuildTaskDTO> rebuildBookVersions(
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.accepted().body(bookVersionRebuildTaskService.start(user));
    }

    @GetMapping("/versions/rebuild/{taskId}")
    public ResponseEntity<BookVersionRebuildTaskDTO> getBookVersionRebuildProgress(
            Authentication authentication,
            @PathVariable String taskId) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(bookVersionRebuildTaskService.get(taskId, user));
    }

    /**
     * 获取收藏书籍
     */
    @GetMapping("/favorites")
    public ResponseEntity<Page<BookDTO>> getFavoriteBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<BookDTO> books = bookService.getFavoriteBooks(user, pageRequest);
        return ResponseEntity.ok(books);
    }

    /**
     * 获取想读书籍
     */
    @GetMapping("/wanted")
    public ResponseEntity<Page<BookDTO>> getWantedBooks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<BookDTO> books = bookService.getWantedBooks(user, pageRequest);
        return ResponseEntity.ok(books);
    }

    /**
     * 搜索书籍
     */
    @GetMapping("/search")
    public ResponseEntity<Page<BookDTO>> searchBooks(
            Authentication authentication,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<BookDTO> books = bookService.searchBooks(user, keyword, pageRequest);
        return ResponseEntity.ok(books);
    }

    /**
     * 获取回收站书籍。
     */
    @GetMapping("/trash")
    public ResponseEntity<Page<BookDTO>> getTrash(
            Authentication authentication,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.findByUsername(authentication.getName());
        PageRequest pageRequest = PageRequest.of(
                page, size, Sort.by("deletedAt").descending());
        return ResponseEntity.ok(
                bookService.getTrash(user, keyword, pageRequest));
    }

    @GetMapping("/trash/count")
    public ResponseEntity<Map<String, Long>> getTrashCount(
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(Map.of("count", bookService.getTrashCount(user)));
    }

    /**
     * 批量移入回收站。只更新数据库，不删除原始文件。
     */
    @PostMapping("/trash/move")
    public ResponseEntity<Void> moveBooksToTrash(
            Authentication authentication,
            @Valid @RequestBody BookIdsRequest request) {
        User user = userService.findByUsername(authentication.getName());
        bookService.moveBooksToTrash(request.getBookIds(), user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trash/restore")
    public ResponseEntity<List<BookDTO>> restoreBooks(
            Authentication authentication,
            @Valid @RequestBody BookIdsRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(
                bookService.restoreBooks(request.getBookIds(), user));
    }

    /**
     * 从回收站永久移除；保留防止扫描重复导入的墓碑，原始文件始终保留。
     */
    @PostMapping("/trash/permanent")
    public ResponseEntity<Void> permanentlyDeleteBooks(
            Authentication authentication,
            @Valid @RequestBody BookIdsRequest request) {
        User user = userService.findByUsername(authentication.getName());
        bookService.permanentlyDeleteBooks(request.getBookIds(), user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 清空回收站；保留防止扫描重复导入的墓碑，原始文件始终保留。
     */
    @DeleteMapping("/trash")
    public ResponseEntity<Void> emptyTrash(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        bookService.emptyTrash(user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取书籍详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookDTO> getBookById(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO book = bookService.getBookById(id, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 获取书籍章节目录。
     */
    @GetMapping("/{id}/toc")
    public ResponseEntity<List<BookTocItemDTO>> getBookTableOfContents(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) Long versionId) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);
        return ResponseEntity.ok(bookParsingService.getTableOfContents(
                bookVersionService.toReadableBook(book, version)));
    }

    /**
     * 获取所有书籍（无分页）
     */
    @GetMapping("/all")
    public ResponseEntity<List<BookDTO>> getAllBooks(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<BookDTO> books = bookService.getAllBooks(user);
        return ResponseEntity.ok(books);
    }

    /**
     * 切换收藏状态
     */
    @PutMapping("/{id}/favorite")
    public ResponseEntity<BookDTO> toggleFavorite(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO book = bookService.toggleFavorite(id, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 切换想读状态
     */
    @PutMapping("/{id}/wanted")
    public ResponseEntity<BookDTO> toggleWanted(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO book = bookService.toggleWanted(id, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 将书籍移入回收站，原始文件始终保留。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        bookService.deleteBook(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新书籍元数据
     */
    @PutMapping("/{id}/metadata")
    public ResponseEntity<BookDTO> updateBookMetadata(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody BookDTO bookDTO) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO updatedBook = bookService.updateBookMetadata(id, bookDTO, user);
        return ResponseEntity.ok(updatedBook);
    }

    /**
     * 更新阅读状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<BookDTO> updateReadingStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        User user = userService.findByUsername(authentication.getName());
        Book.ReadingStatus status = Book.ReadingStatus.valueOf(body.get("status"));
        BookDTO book = bookService.updateReadingStatus(id, status, user);
        return ResponseEntity.ok(book);
    }

    /**
     * 设置或清除单本书籍分类。
     */
    @PutMapping("/{id}/category")
    public ResponseEntity<BookDTO> updateBookCategory(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody BookCategoryRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(
                bookService.updateBookCategory(id, request.getCategoryId(), user));
    }

    /**
     * 批量设置或清除书籍分类。
     */
    @PutMapping("/batch/category")
    public ResponseEntity<List<BookDTO>> updateBookCategories(
            Authentication authentication,
            @Valid @RequestBody BookCategoryRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(bookService.updateBookCategories(
                request.getBookIds(), request.getCategoryId(), user));
    }

    /**
     * 替换单本书籍的全部标签。
     */
    @PutMapping("/{id}/tags")
    public ResponseEntity<BookDTO> updateBookTags(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody BookTagsRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(
                bookService.updateBookTags(id, request.getTagIds(), user));
    }

    /**
     * 批量添加、移除或替换书籍标签。
     */
    @PutMapping("/batch/tags")
    public ResponseEntity<List<BookDTO>> updateBookTags(
            Authentication authentication,
            @Valid @RequestBody BookTagsRequest request) {
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(bookService.updateBookTags(
                request.getBookIds(), request.getTagIds(), request.getMode(), user));
    }

    /**
     * 获取书籍文件内容（用于在线阅读）
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> getBookContent(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) Long versionId,
            WebRequest webRequest) throws IOException {

        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);

        Path filePath = Paths.get(version.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(filePath.toFile());
        String contentType = MimeTypeUtil.getContentTypeWithCharset(version.getFormat());
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        long fileSize = Files.size(filePath);
        String etag = "\""
                + Long.toHexString(fileSize)
                + "-"
                + Long.toHexString(lastModified)
                + "\"";
        CacheControl cacheControl = CacheControl.maxAge(Duration.ofDays(1))
                .cachePrivate();

        if (webRequest.checkNotModified(etag, lastModified)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .lastModified(lastModified)
                    .cacheControl(cacheControl)
                    .build();
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .eTag(etag)
                .lastModified(lastModified)
                .cacheControl(cacheControl);

        // PDF 文件需要 inline 显示，而不是下载
        if ("pdf".equalsIgnoreCase(version.getFormat())) {
            responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        }

        return responseBuilder.body(resource);
    }

    /**
     * 获取TXT处理后的内容（带段落结构 + 章节信息）
     */
    @GetMapping("/{id}/content-processed")
    public ResponseEntity<Map<String, String>> getProcessedContent(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) Long versionId) {

        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);

        Path filePath = Paths.get(version.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String rawText = txtParserService.readFileWithEncoding(filePath);
            String processedText = txtParserService.processText(rawText);
            return ResponseEntity.ok(Map.of(
                    "text", processedText,
                    "chapterInfo",
                    version.getChapterInfo() != null ? version.getChapterInfo() : "[]"
            ));
        } catch (Exception e) {
            log.error("处理TXT内容失败: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 重新解析TXT章节信息
     */
    @PostMapping("/{id}/parse-chapters")
    public ResponseEntity<Map<String, Object>> parseChapters(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);

        if (!"txt".equals(book.getFormat()) && !"md".equals(book.getFormat())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "仅支持TXT/MD格式"
            ));
        }

        try {
            String chapterInfo = txtParserService.parseChapters(Paths.get(book.getFilePath()));
            book.setChapterInfo(chapterInfo);
            book.setChapterCount(chapterInfo.split("\"title\"").length - 1);
            bookRepository.save(book);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "chapterInfo", chapterInfo
            ));
        } catch (Exception e) {
            log.error("解析章节失败: {}", book.getFilePath(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "解析失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 从原始文件重新解析书籍元数据和章节信息。
     */
    @PostMapping("/{id}/reparse")
    public ResponseEntity<Map<String, Object>> reparseBook(
            Authentication authentication,
            @PathVariable Long id) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        BookParsingService.ParseResult result = bookParsingService.reparse(book);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", result.getMessage());
        body.put("updatedFields", result.getUpdatedFields());
        body.put("chapterCount", result.getChapterCount());
        body.put("book", bookService.convertToDTO(result.getBook()));
        return ResponseEntity.ok(body);
    }

    /**
     * 上传并替换书籍封面。
     */
    @PostMapping(value = "/{id}/cover-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookDTO> uploadCover(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        return ResponseEntity.ok(
                bookService.convertToDTO(bookCoverService.upload(book, file)));
    }

    /**
     * 刮削书籍元数据
     */
    @PostMapping("/{id}/scrape")
    public ResponseEntity<Map<String, Object>> scrapeBook(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        Book scrapedBook = metadataScrapingService.scrapeBook(book);
        BookDTO bookDTO = bookService.convertToDTO(scrapedBook);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "book", bookDTO
        ));
    }

    /**
     * 下载书籍封面
     */
    @PostMapping("/{id}/cover")
    public ResponseEntity<Map<String, Object>> downloadCover(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        Book book = bookService.getBookEntity(id, user);
        Book updatedBook = coverDownloadService.downloadCover(book);
        BookDTO bookDTO = bookService.convertToDTO(updatedBook);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "book", bookDTO
        ));
    }

    /**
     * 批量刮削指定书籍
     */
    @PostMapping("/batch-scrape")
    public ResponseEntity<Map<String, String>> batchScrape(
            Authentication authentication,
            @Valid @RequestBody BatchScrapeRequest request) {

        User user = userService.findByUsername(authentication.getName());
        String taskId = batchScrapeTaskService.createTask(request.getBookIds(), user, request.isForceUpdate());

        return ResponseEntity.ok(Map.of("taskId", taskId));
    }

    /**
     * 刮削所有缺少元数据的书籍
     */
    @PostMapping("/scrape-all-incomplete")
    public ResponseEntity<Map<String, String>> scrapeAllIncomplete(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean forceUpdate) {

        User user = userService.findByUsername(authentication.getName());
        String taskId = batchScrapeTaskService.createScrapeAllIncompleteTask(user, forceUpdate);

        return ResponseEntity.ok(Map.of("taskId", taskId));
    }

    /**
     * 查询刮削任务状态（轮询用）
     */
    @GetMapping("/scrape-task/{taskId}")
    public ResponseEntity<ScrapeTaskDTO> getScrapeTask(
            @PathVariable String taskId) {

        ScrapeTaskDTO task = batchScrapeTaskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(task);
    }

    /**
     * SSE实时推送刮削进度
     */
    @GetMapping(value = "/scrape-task/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamScrapeProgress(@PathVariable String taskId) {

        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        ScrapeTaskDTO task = batchScrapeTaskService.getTask(taskId);
        if (task == null) {
            emitter.completeWithError(new IllegalArgumentException("任务不存在"));
            return emitter;
        }

        // 如果任务已完成，直接发送最终状态
        if ("COMPLETED".equals(task.getStatus()) ||
            "FAILED".equals(task.getStatus()) ||
            "CANCELLED".equals(task.getStatus())) {
            try {
                emitter.send(SseEmitter.event()
                        .name("scrape-progress")
                        .data(task));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 注册SSE连接
        batchScrapeTaskService.addSseEmitter(taskId, emitter);

        return emitter;
    }

    /**
     * 取消刮削任务
     */
    @PostMapping("/scrape-task/{taskId}/cancel")
    public ResponseEntity<Map<String, Boolean>> cancelScrapeTask(
            @PathVariable String taskId) {

        boolean cancelled = batchScrapeTaskService.cancelTask(taskId);

        return ResponseEntity.ok(Map.of("cancelled", cancelled));
    }

    /**
     * 保存读书笔记
     */
    @PutMapping("/{id}/notes")
    public ResponseEntity<Map<String, Object>> saveNotes(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        User user = userService.findByUsername(authentication.getName());
        String notes = body.get("notes");

        try {
            Book book = bookService.getBookEntity(id, user);
            book.setNotes(notes);
            bookRepository.save(book);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "笔记保存成功"
            ));
        } catch (Exception e) {
            log.error("保存笔记失败: {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "保存失败: " + e.getMessage()
            ));
        }
    }
}
