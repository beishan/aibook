package com.aibook.controller;

import com.aibook.dto.crawler.CrawlerDtos.*;
import com.aibook.model.entity.*;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.service.UserService;
import com.aibook.service.crawler.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
public class CrawlerController {
    private final UserService userService;
    private final CrawlerManagementService managementService;
    private final CrawlerTaskService taskService;
    private final CrawlerExportService exportService;
    private final CrawlerChapterRepository chapterRepository;

    @GetMapping("/dashboard") public DashboardView dashboard(Authentication auth) { return managementService.dashboard(user(auth)); }
    @GetMapping("/sites") public List<SiteView> sites(Authentication auth) { return managementService.sites(user(auth)); }
    @PostMapping("/sites") @ResponseStatus(HttpStatus.CREATED) public SiteView createSite(Authentication auth, @Valid @RequestBody SitePayload payload) { return managementService.createSite(user(auth), payload); }
    @PutMapping("/sites/{id}") public SiteView updateSite(Authentication auth, @PathVariable Long id, @Valid @RequestBody SitePayload payload) { return managementService.updateSite(user(auth), id, payload); }
    @DeleteMapping("/sites/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSite(Authentication auth, @PathVariable Long id) { managementService.deleteSite(user(auth), id); }
    @PostMapping("/sites/{id}/crawl") @ResponseStatus(HttpStatus.ACCEPTED) public TaskView crawl(Authentication auth, @PathVariable Long id, @Valid @RequestBody ManualCrawlRequest request) { return taskService.start(user(auth), id, request.url()); }

    @GetMapping("/books") public Page<BookView> books(Authentication auth, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return managementService.books(user(auth), page, size); }
    @GetMapping("/books/{id}") public BookView book(Authentication auth, @PathVariable Long id) { return managementService.book(user(auth), id); }
    @GetMapping("/books/{id}/chapters") public List<ChapterView> chapters(Authentication auth, @PathVariable Long id) { return managementService.chapters(user(auth), id); }
    @GetMapping("/books/{bookId}/chapters/{chapterId}") public Map<String, Object> chapter(Authentication auth, @PathVariable Long bookId, @PathVariable Long chapterId) {
        CrawlerBook book = managementService.ownedBook(user(auth), bookId);
        CrawlerChapter chapter = chapterRepository.findById(chapterId).filter(ch -> ch.getCrawlerBook().getId().equals(book.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集章节不存在"));
        Map<String, Object> value = new LinkedHashMap<>(); value.put("id", chapter.getId()); value.put("title", chapter.getChapterName()); value.put("url", chapter.getChapterUrl()); value.put("content", Objects.toString(chapter.getContent(), "")); value.put("errorMessage", Objects.toString(chapter.getErrorMessage(), "")); return value;
    }
    @PostMapping("/books/{id}/continue") @ResponseStatus(HttpStatus.ACCEPTED) public TaskView continueBook(Authentication auth, @PathVariable Long id) { return taskService.continueBook(user(auth), id); }
    @PostMapping("/books/{id}/retry-failures") @ResponseStatus(HttpStatus.ACCEPTED) public TaskView retryFailures(Authentication auth, @PathVariable Long id) { return taskService.retryFailures(user(auth), id); }
    @PostMapping("/books/{id}/exports") public List<ExportView> generate(Authentication auth, @PathVariable Long id, @Valid @RequestBody ExportRequest request) { return exportService.generate(user(auth), id, request.formats()); }
    @GetMapping("/books/{id}/exports") public List<ExportView> exports(Authentication auth, @PathVariable Long id) { return exportService.list(user(auth), id); }
    @PostMapping("/books/{id}/import") public Map<String, Long> importLibrary(Authentication auth, @PathVariable Long id, @Valid @RequestBody ImportRequest request) { return Map.of("bookId", exportService.importLibrary(user(auth), id, request.format())); }
    @GetMapping("/books/{bookId}/exports/{exportId}/download") public ResponseEntity<FileSystemResource> download(Authentication auth, @PathVariable Long bookId, @PathVariable Long exportId) {
        Path path = exportService.exportPath(user(auth), bookId, exportId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"").contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(path));
    }

    @GetMapping("/tasks") public List<TaskView> tasks(Authentication auth, @RequestParam(defaultValue = "50") int limit) { return managementService.tasks(user(auth), limit); }
    @PostMapping("/tasks/{id}/{command}") public TaskView taskCommand(Authentication auth, @PathVariable String id, @PathVariable String command) { return taskService.command(user(auth), id, command); }

    private User user(Authentication auth) { return userService.findByUsername(auth.getName()); }
}
