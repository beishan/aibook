package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerBookExport;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.CrawlerBookExportRepository;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrawlerExportServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void generatesPartialBookUsingOnlyChaptersWithContent() throws Exception {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerBook book = book(user);
        CrawlerChapter available = CrawlerChapter.builder().crawlerBook(book).chapterIndex(0)
                .chapterName("第一章").content("第一章正文").build();
        CrawlerChapter missing = CrawlerChapter.builder().crawlerBook(book).chapterIndex(1)
                .chapterName("第二章").content(" ").build();
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        CrawlerBookExportRepository exports = mock(CrawlerBookExportRepository.class);
        when(management.ownedBook(user, 3L)).thenReturn(book);
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(book)).thenReturn(List.of(available, missing));
        when(exports.findByCrawlerBookAndFormat(book, "TXT")).thenReturn(Optional.empty());
        when(exports.save(any(CrawlerBookExport.class))).thenAnswer(invocation -> {
            CrawlerBookExport saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        CrawlerExportService service = service(management, chapters, exports);

        var result = service.generate(user, 3L, List.of("TXT"));

        assertThat(result).singleElement().extracting(item -> item.format()).isEqualTo("TXT");
        String text = Files.readString(temporaryDirectory.resolve("exports/3.txt"));
        assertThat(text).contains("第一章", "第一章正文").doesNotContain("第二章");
    }

    @Test
    void rejectsExportWhenNoChapterHasContent() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerBook book = book(user);
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        when(management.ownedBook(user, 3L)).thenReturn(book);
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(book)).thenReturn(List.of(
                CrawlerChapter.builder().crawlerBook(book).chapterName("空章节").content(null).build()));
        CrawlerExportService service = service(management, chapters, mock(CrawlerBookExportRepository.class));

        assertThatThrownBy(() -> service.generate(user, 3L, List.of("EPUB")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("没有可用正文");
    }

    private CrawlerExportService service(CrawlerManagementService management,
            CrawlerChapterRepository chapters, CrawlerBookExportRepository exports) {
        CrawlerExportService service = new CrawlerExportService(management, chapters, exports,
                mock(CrawlerBookRepository.class), mock(BookRepository.class), mock(BookVersionRepository.class),
                mock(OperationLogService.class));
        ReflectionTestUtils.setField(service, "storagePath", temporaryDirectory.toString());
        ReflectionTestUtils.setField(service, "uploadPath", temporaryDirectory.resolve("uploads").toString());
        return service;
    }

    private CrawlerBook book(User user) {
        CrawlerSite site = CrawlerSite.builder().id(2L).user(user).siteName("示例站").siteCode("demo").build();
        return CrawlerBook.builder().id(3L).site(site).bookName("部分采集书")
                .chapterCount(2).crawledChapterCount(1).failedChapterCount(1)
                .crawlStatus(CrawlerBook.CrawlStatus.PARTIAL_SUCCESS).build();
    }
}
