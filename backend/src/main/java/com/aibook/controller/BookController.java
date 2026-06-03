package com.aibook.controller;

import com.aibook.dto.BatchScrapeRequest;
import com.aibook.dto.BookDTO;
import com.aibook.dto.ScrapeTaskDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.service.BookService;
import com.aibook.service.UserService;
import com.aibook.service.scraper.BatchScrapeTaskService;
import com.aibook.service.scraper.CoverDownloadService;
import com.aibook.service.scraper.MetadataScrapingService;
import com.aibook.util.MimeTypeUtil;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 书籍控制器
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookController {

    private final BookService bookService;
    private final UserService userService;
    private final MetadataScrapingService metadataScrapingService;
    private final CoverDownloadService coverDownloadService;
    private final BatchScrapeTaskService batchScrapeTaskService;

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
            books = bookService.getBooksByCategory(user, categoryId, pageRequest);
        } else if (tagId != null) {
            books = bookService.getBooksByTag(user, tagId, pageRequest);
        } else {
            books = bookService.getBooks(user, pageRequest);
        }

        return ResponseEntity.ok(books);
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
     * 删除书籍
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
     * 获取书籍文件内容（用于在线阅读）
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> getBookContent(
            Authentication authentication,
            @PathVariable Long id) {

        User user = userService.findByUsername(authentication.getName());
        BookDTO bookDTO = bookService.getBookById(id, user);

        Path filePath = Paths.get(bookDTO.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(filePath.toFile());
        String contentType = MimeTypeUtil.getContentTypeWithCharset(bookDTO.getFormat());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
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
}
