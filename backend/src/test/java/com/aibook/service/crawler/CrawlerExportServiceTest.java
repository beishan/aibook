package com.aibook.service.crawler;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerBookExport;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.User;
import com.aibook.model.entity.Tag;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.CrawlerBookExportRepository;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.aibook.repository.TagRepository;
import com.aibook.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

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

    @Test
    void reusesUnchangedEpubAndUsesStableChapterNames() throws Exception {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerBook book = book(user);
        book.setExternalBookId("book-3");
        CrawlerChapter chapter = CrawlerChapter.builder().crawlerBook(book).chapterIndex(0)
                .externalChapterId("chapter-100").chapterUrl("https://example.com/100")
                .chapterName("第一章").content("正文").contentHash("content-1").build();
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        CrawlerBookExportRepository exports = mock(CrawlerBookExportRepository.class);
        AtomicReference<CrawlerBookExport> savedExport = new AtomicReference<>();
        when(management.ownedBook(user, 3L)).thenReturn(book);
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(book)).thenReturn(List.of(chapter));
        when(exports.findByCrawlerBookAndFormat(book, "EPUB"))
                .thenAnswer(invocation -> Optional.ofNullable(savedExport.get()));
        when(exports.save(any(CrawlerBookExport.class))).thenAnswer(invocation -> {
            CrawlerBookExport saved = invocation.getArgument(0);
            saved.setId(9L);
            savedExport.set(saved);
            return saved;
        });
        CrawlerExportService service = service(management, chapters, exports);

        service.generate(user, 3L, List.of("EPUB"));
        service.generate(user, 3L, List.of("EPUB"));

        verify(exports, times(1)).save(any(CrawlerBookExport.class));
        try (ZipFile zip = new ZipFile(temporaryDirectory.resolve("exports/3.epub").toFile())) {
            assertThat(zip.stream().map(entry -> entry.getName()))
                    .anyMatch(name -> name.matches("OEBPS/chapter-[a-f0-9]{20}\\.xhtml"))
                    .noneMatch(name -> name.equals("OEBPS/chapter-0001.xhtml"));
            String opf = new String(zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readAllBytes());
            assertThat(opf).contains("urn:uuid:").doesNotContain("null</dc:identifier>");
        }
    }

    @Test
    void publishesNewPrimaryVersionAndMigratesReadingProgress() {
        User user = User.builder().id(1L).username("owner").build();
        Book libraryBook = Book.builder().id(20L).user(user).title("旧书名").author("旧作者")
                .format("epub").filePath("old.epub").fileHash("old-hash").chapterCount(1).build();
        CrawlerBook crawlerBook = book(user);
        crawlerBook.setExternalBookId("book-3");
        crawlerBook.setLibraryBook(libraryBook);
        CrawlerChapter chapter = CrawlerChapter.builder().crawlerBook(crawlerBook).chapterIndex(0)
                .externalChapterId("chapter-1").chapterUrl("https://example.com/chapter-1")
                .chapterName("第一章").content("更新后的正文").contentHash("new-content").build();
        BookVersion oldVersion = BookVersion.builder().id(21L).book(libraryBook).format("epub")
                .filePath("old.epub").fileHash("old-hash").primaryVersion(true)
                .sourceType("CRAWLER").sourceId("3").build();
        VersionReadingProgress oldProgress = VersionReadingProgress.builder().user(user).version(oldVersion)
                .currentChapter("epubcfi(/6/2)").currentChapterTitle("第一章").totalProgress(42).build();
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        CrawlerBookExportRepository exports = mock(CrawlerBookExportRepository.class);
        CrawlerBookRepository crawlerBooks = mock(CrawlerBookRepository.class);
        BookRepository books = mock(BookRepository.class);
        BookVersionRepository versions = mock(BookVersionRepository.class);
        VersionReadingProgressRepository progresses = mock(VersionReadingProgressRepository.class);
        AtomicReference<CrawlerBookExport> savedExport = new AtomicReference<>();
        AtomicReference<BookVersion> newVersion = new AtomicReference<>();
        when(management.ownedBook(user, 3L)).thenReturn(crawlerBook);
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(crawlerBook)).thenReturn(List.of(chapter));
        when(exports.findByCrawlerBookAndFormat(crawlerBook, "EPUB"))
                .thenAnswer(invocation -> Optional.ofNullable(savedExport.get()));
        when(exports.save(any(CrawlerBookExport.class))).thenAnswer(invocation -> {
            CrawlerBookExport saved = invocation.getArgument(0); saved.setId(30L); savedExport.set(saved); return saved;
        });
        when(versions.findByBookOrderByPrimaryVersionDescCreatedAtAsc(libraryBook)).thenAnswer(invocation ->
                newVersion.get() == null ? List.of(oldVersion) : List.of(newVersion.get(), oldVersion));
        when(versions.findByFileHash(anyString())).thenReturn(Optional.empty());
        when(versions.save(any(BookVersion.class))).thenAnswer(invocation -> {
            BookVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) { saved.setId(22L); newVersion.set(saved); }
            return saved;
        });
        when(progresses.findByUserAndVersion(user, oldVersion)).thenReturn(Optional.of(oldProgress));
        CrawlerExportService service = new CrawlerExportService(management, chapters, exports,
                crawlerBooks, books, mock(CategoryRepository.class), mock(TagRepository.class),
                versions, progresses, mock(OperationLogService.class));
        ReflectionTestUtils.setField(service, "storagePath", temporaryDirectory.toString());
        ReflectionTestUtils.setField(service, "uploadPath", temporaryDirectory.resolve("uploads").toString());

        int published = service.syncImportedBook(user, crawlerBook.getId());

        assertThat(published).isEqualTo(1);
        assertThat(oldVersion.getPrimaryVersion()).isFalse();
        assertThat(newVersion.get()).isNotNull();
        assertThat(newVersion.get().getPrimaryVersion()).isTrue();
        assertThat(libraryBook.getFilePath()).isEqualTo(newVersion.get().getFilePath());
        assertThat(libraryBook.getChapterCount()).isEqualTo(1);
        verify(progresses).save(argThat(progress -> progress.getVersion() == newVersion.get()
                && progress.getTotalProgress() == 42 && "第一章".equals(progress.getCurrentChapterTitle())));
    }

    @Test
    void importsSelectedFormatsAsVersionsOfOneLibraryBook() {
        User user = User.builder().id(1L).username("owner").build();
        CrawlerBook crawlerBook = book(user);
        crawlerBook.setCategory("都市");
        crawlerBook.setTags("后宫\nNTR\n后宫");
        crawlerBook.setBookStatus("连载中");
        CrawlerChapter chapter = CrawlerChapter.builder().crawlerBook(crawlerBook).chapterIndex(0)
                .chapterName("第一章").content("正文").contentHash("content-1").build();
        CrawlerManagementService management = mock(CrawlerManagementService.class);
        CrawlerChapterRepository chapters = mock(CrawlerChapterRepository.class);
        CrawlerBookExportRepository exports = mock(CrawlerBookExportRepository.class);
        CrawlerBookRepository crawlerBooks = mock(CrawlerBookRepository.class);
        BookRepository books = mock(BookRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        TagRepository tags = mock(TagRepository.class);
        BookVersionRepository versions = mock(BookVersionRepository.class);
        Map<String, CrawlerBookExport> savedExports = new HashMap<>();
        List<BookVersion> savedVersions = new ArrayList<>();
        when(management.ownedBook(user, 3L)).thenReturn(crawlerBook);
        when(chapters.findByCrawlerBookOrderByChapterIndexAsc(crawlerBook)).thenReturn(List.of(chapter));
        when(exports.findByCrawlerBookAndFormat(eq(crawlerBook), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(savedExports.get(invocation.getArgument(1))));
        when(exports.save(any(CrawlerBookExport.class))).thenAnswer(invocation -> {
            CrawlerBookExport saved = invocation.getArgument(0);
            saved.setId((long) savedExports.size() + 1);
            savedExports.put(saved.getFormat(), saved);
            return saved;
        });
        when(books.save(any(Book.class))).thenAnswer(invocation -> {
            Book saved = invocation.getArgument(0); saved.setId(20L); return saved;
        });
        when(books.findByFileHash(anyString())).thenReturn(Optional.empty());
        when(categories.findFirstByUserAndNameIgnoreCase(user, "都市")).thenReturn(Optional.empty());
        when(categories.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tags.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findByFileHash(anyString())).thenReturn(Optional.empty());
        when(versions.findByBookOrderByPrimaryVersionDescCreatedAtAsc(any(Book.class)))
                .thenAnswer(invocation -> List.copyOf(savedVersions));
        when(versions.save(any(BookVersion.class))).thenAnswer(invocation -> {
            BookVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(30L + savedVersions.size());
            if (!savedVersions.contains(saved)) savedVersions.add(saved);
            return saved;
        });
        CrawlerExportService service = new CrawlerExportService(management, chapters, exports,
                crawlerBooks, books, categories, tags,
                versions, mock(VersionReadingProgressRepository.class),
                mock(OperationLogService.class));
        ReflectionTestUtils.setField(service, "storagePath", temporaryDirectory.toString());
        ReflectionTestUtils.setField(service, "uploadPath", temporaryDirectory.resolve("uploads").toString());

        Long libraryBookId = service.importLibrary(user, crawlerBook.getId(), List.of("txt", "EPUB", "TXT"));

        assertThat(libraryBookId).isEqualTo(20L);
        assertThat(savedVersions).extracting(BookVersion::getFormat).containsExactlyInAnyOrder("epub", "txt");
        assertThat(savedVersions).filteredOn(BookVersion::getPrimaryVersion).singleElement()
                .extracting(BookVersion::getFormat).isEqualTo("epub");
        assertThat(crawlerBook.getLibraryBook()).isNotNull();
        assertThat(crawlerBook.getLibraryBook().getCategory().getName()).isEqualTo("都市");
        assertThat(crawlerBook.getLibraryBook().getTags()).extracting(Tag::getName)
                .containsExactlyInAnyOrder("后宫", "NTR");
        assertThat(crawlerBook.getLibraryBook().getSourceBookStatus()).isEqualTo("连载中");
        verify(crawlerBooks).save(crawlerBook);
    }

    private CrawlerExportService service(CrawlerManagementService management,
            CrawlerChapterRepository chapters, CrawlerBookExportRepository exports) {
        CrawlerExportService service = new CrawlerExportService(management, chapters, exports,
                mock(CrawlerBookRepository.class), mock(BookRepository.class),
                mock(CategoryRepository.class), mock(TagRepository.class), mock(BookVersionRepository.class),
                mock(VersionReadingProgressRepository.class),
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
