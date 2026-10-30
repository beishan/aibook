package com.aibook.service.crawler;

import com.aibook.library.LibraryService;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerBookRepository;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrawlerImportLibrariesService {
    private final CrawlerBookRepository books;
    private final LibraryService libraries;

    public static List<Long> decode(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(Long::valueOf).distinct().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<Long> get(User user, Long bookId) {
        return decode(books.findByIdAndSiteUser(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集书籍不存在"))
                .getAutoImportLibraryIds());
    }

    @Transactional
    public List<Long> save(User user, Long bookId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.size() > 20
                || requestedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择最多 20 个图书馆");
        }
        // 与完成入库共用行锁，使运行中的任务读取最新设置。
        CrawlerBook book = books.findForLibraryImport(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集书籍不存在"));
        List<Long> ids = requestedIds.stream().distinct().sorted().toList();
        for (Long id : ids) libraries.library(user, id);
        String encoded = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (books.updateImportLibraries(bookId, user, encoded,
                !ids.isEmpty() || Boolean.TRUE.equals(book.getAutoImportEnabled())) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "采集书籍已变化，请重试");
        }
        return ids;
    }

    public void addCompleted(User user, Long libraryBookId, List<Long> ids) {
        if (ids.isEmpty()) return;
        // LibraryService 使用 JDBC；先刷新 JPA 入库结果，确保同一事务内可见。
        books.flush();
        var results = libraries.add(user, ids, List.of(libraryBookId), null);
        if (results.stream().anyMatch(item -> "failed".equals(item.status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "自动加入图书馆失败：目标馆已删除或不可用，请重新选择图书馆并保存设置");
        }
    }
}
