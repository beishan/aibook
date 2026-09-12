package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.ExportView;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerExportService {
    private final CrawlerManagementService managementService;
    private final CrawlerChapterRepository chapterRepository;
    private final CrawlerBookExportRepository exportRepository;
    private final CrawlerBookRepository crawlerBookRepository;
    private final BookRepository bookRepository;
    private final BookVersionRepository versionRepository;
    private final VersionReadingProgressRepository versionProgressRepository;
    private final OperationLogService operationLogService;

    @Value("${crawler.storage-path:./crawler-data}") private String storagePath;
    @Value("${upload.path:./uploads}") private String uploadPath;

    @Transactional
    public List<ExportView> generate(User user, Long bookId, List<String> formats) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        List<CrawlerChapter> chapters = availableChapters(book);
        validateHasContent(chapters);
        List<ExportView> result = new ArrayList<>();
        for (String requested : new LinkedHashSet<>(formats)) {
            String format = requested.toUpperCase(Locale.ROOT);
            if (!Set.of("TXT", "EPUB").contains(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持生成 TXT 或 EPUB");
            Path output = Path.of(storagePath).resolve("exports").resolve(book.getId() + "." + format.toLowerCase(Locale.ROOT));
            String sourceHash = sourceHash(book, chapters, format);
            Optional<CrawlerBookExport> existing = exportRepository.findByCrawlerBookAndFormat(book, format);
            if (existing.filter(item -> sourceHash.equals(item.getSourceHash()))
                    .map(CrawlerBookExport::getFilePath).map(Path::of).filter(Files::isRegularFile).isPresent()) {
                result.add(view(existing.orElseThrow()));
                continue;
            }
            Path temporary = null;
            try {
                Files.createDirectories(output.getParent());
                temporary = Files.createTempFile(output.getParent(), book.getId() + "-", ".tmp");
                if ("TXT".equals(format)) writeTxt(temporary, book, chapters); else writeEpub(temporary, book, chapters);
                replaceFile(temporary, output);
                temporary = null;
                CrawlerBookExport export = existing.orElseGet(CrawlerBookExport::new);
                export.setCrawlerBook(book); export.setFormat(format); export.setFilePath(output.toString()); export.setFileSize(Files.size(output)); export.setFileHash(hash(output)); export.setSourceHash(sourceHash);
                result.add(view(exportRepository.save(export)));
            } catch (IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成 " + format + " 失败", exception); }
            finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
        }
        recordOperation(user, book, "生成采集书籍文件：" + book.getBookName(),
                "格式：" + String.join(",", result.stream().map(ExportView::format).toList())
                        + "；可用章节：" + chapters.size() + "/" + value(book.getChapterCount()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ExportView> list(User user, Long bookId) {
        return exportRepository.findByCrawlerBookOrderByFormatAsc(managementService.ownedBook(user, bookId)).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Path exportPath(User user, Long bookId, Long exportId) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        CrawlerBookExport export = exportRepository.findById(exportId)
                .filter(item -> item.getCrawlerBook().getId().equals(book.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "导出文件不存在"));
        Path path = Path.of(export.getFilePath());
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出文件已丢失，请重新生成");
        return path;
    }

    @Transactional
    public Long importLibrary(User user, Long bookId, List<String> requestedFormats) {
        CrawlerBook crawlerBook = managementService.ownedBook(user, bookId);
        int availableChapterCount = availableChapters(crawlerBook).size();
        if (availableChapterCount == 0) throw new ResponseStatusException(
                HttpStatus.CONFLICT, "书籍还没有可用正文，不能生成或加入书库");
        LinkedHashSet<String> formats = normalizeFormats(requestedFormats);
        if (crawlerBook.getLibraryBook() != null) {
            formats.forEach(format -> addSecondaryVersion(crawlerBook, format));
            syncImportedBook(user, bookId, formats);
            return crawlerBook.getLibraryBook().getId();
        }
        String format = formats.contains("EPUB") ? "EPUB" : formats.getFirst();
        generate(user, bookId, List.of(format));
        CrawlerBookExport source = exportRepository.findByCrawlerBookAndFormat(crawlerBook, format).orElseThrow();
        Path sourcePath = Path.of(source.getFilePath());
        Path target = Path.of(uploadPath).resolve(UUID.randomUUID() + "." + format.toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(target.getParent()); Files.copy(sourcePath, target);
            String fileHash = hash(target);
            if (bookRepository.findByFileHash(fileHash).isPresent() || versionRepository.findByFileHash(fileHash).isPresent()) {
                Files.deleteIfExists(target);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该采集版本已存在于书库");
            }
            Book book = bookRepository.save(Book.builder().title(crawlerBook.getBookName()).author(crawlerBook.getAuthor())
                    .description(crawlerBook.getDescription()).coverUrl(crawlerBook.getCoverUrl())
                    .format(format.toLowerCase(Locale.ROOT)).filePath(target.toString()).fileSize(Files.size(target))
                    .fileHash(fileHash).sourceType(Book.SourceType.CRAWLER).user(user)
                    .chapterCount(availableChapterCount).build());
            versionRepository.save(BookVersion.builder().book(book).displayName(safe(crawlerBook.getBookName()) + "." + format.toLowerCase(Locale.ROOT))
                    .format(format.toLowerCase(Locale.ROOT)).filePath(target.toString()).fileSize(Files.size(target)).fileHash(fileHash)
                    .primaryVersion(true).chapterCount(availableChapterCount).sourceType("CRAWLER")
                    .sourceId(crawlerBook.getId().toString()).sourceSite(crawlerBook.getSite().getSiteCode()).sourceUrl(crawlerBook.getBookUrl()).build());
            crawlerBook.setLibraryBook(book); crawlerBook.setImportStatus(CrawlerBook.ImportStatus.IMPORTED);
            crawlerBook.setAutoSyncLibrary(true); crawlerBookRepository.save(crawlerBook);
            recordOperation(user, crawlerBook, "采集书籍加入书库：" + crawlerBook.getBookName(),
                    "格式：" + String.join(",", formats) + "；可用章节：" + availableChapterCount + "/"
                            + value(crawlerBook.getChapterCount()) + "；书库ID：" + book.getId());
            formats.stream().filter(item -> !item.equals(format))
                    .forEach(item -> addSecondaryVersion(crawlerBook, item));
            return book.getId();
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { try { Files.deleteIfExists(target); } catch (Exception ignored) { } throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "加入书库失败", exception); }
    }

    private LinkedHashSet<String> normalizeFormats(Collection<String> requestedFormats) {
        if (requestedFormats == null || requestedFormats.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一种入库格式");
        }
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        for (String requested : requestedFormats) {
            String format = requested == null ? "" : requested.toUpperCase(Locale.ROOT);
            if (!Set.of("TXT", "EPUB").contains(format)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 TXT 或 EPUB 入库");
            }
            formats.add(format);
        }
        return formats;
    }

    private void addSecondaryVersion(CrawlerBook crawlerBook, String format) {
        Book book = crawlerBook.getLibraryBook();
        int availableChapterCount = availableChapters(crawlerBook).size();
        boolean exists = versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(book).stream()
                .anyMatch(version -> format.equalsIgnoreCase(version.getFormat())
                        && "CRAWLER".equals(version.getSourceType())
                        && crawlerBook.getId().toString().equals(version.getSourceId()));
        if (exists) return;
        generate(crawlerBook.getSite().getUser(), crawlerBook.getId(), List.of(format));
        CrawlerBookExport source = exportRepository.findByCrawlerBookAndFormat(crawlerBook, format).orElseThrow();
        Path sourcePath = Path.of(source.getFilePath());
        Path target = Path.of(uploadPath).resolve(UUID.randomUUID() + "." + format.toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(target.getParent()); Files.copy(sourcePath, target);
            String fileHash = hash(target);
            Optional<BookVersion> duplicate = versionRepository.findByFileHash(fileHash);
            if (duplicate.isPresent()) { Files.deleteIfExists(target); return; }
            versionRepository.save(BookVersion.builder().book(book)
                    .displayName(safe(crawlerBook.getBookName()) + "." + format.toLowerCase(Locale.ROOT))
                    .format(format.toLowerCase(Locale.ROOT)).filePath(target.toString()).fileSize(Files.size(target))
                    .fileHash(fileHash).primaryVersion(false).chapterCount(availableChapterCount)
                    .sourceType("CRAWLER").sourceId(crawlerBook.getId().toString())
                    .sourceSite(crawlerBook.getSite().getSiteCode()).sourceUrl(crawlerBook.getBookUrl()).build());
        } catch (Exception exception) {
            try { Files.deleteIfExists(target); } catch (Exception ignored) { }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "添加 " + format + " 版本失败", exception);
        }
    }

    /** 将已入库采集书籍的最新内容发布为同一本书的新版本；内容未变化时不重复创建。 */
    @Transactional
    public int syncImportedBook(User user, Long bookId) {
        return syncImportedBook(user, bookId, null);
    }

    private int syncImportedBook(User user, Long bookId, Collection<String> selectedFormats) {
        CrawlerBook crawlerBook = managementService.ownedBook(user, bookId);
        Book libraryBook = crawlerBook.getLibraryBook();
        if (libraryBook == null) return 0;
        libraryBook.setTitle(crawlerBook.getBookName()); libraryBook.setAuthor(crawlerBook.getAuthor());
        libraryBook.setDescription(crawlerBook.getDescription()); libraryBook.setCoverUrl(crawlerBook.getCoverUrl());
        bookRepository.save(libraryBook);
        List<BookVersion> versions = versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(libraryBook);
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        formats.add(libraryBook.getFormat().toUpperCase(Locale.ROOT));
        versions.stream()
                .filter(version -> "CRAWLER".equals(version.getSourceType()))
                .filter(version -> crawlerBook.getId().toString().equals(version.getSourceId()))
                .map(BookVersion::getFormat).map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> Set.of("EPUB", "TXT").contains(value)).forEach(formats::add);
        if (selectedFormats != null) formats.retainAll(normalizeFormats(selectedFormats));
        int published = 0;
        for (String format : formats) {
            generate(crawlerBook.getSite().getUser(), crawlerBook.getId(), List.of(format));
            CrawlerBookExport export = exportRepository.findByCrawlerBookAndFormat(crawlerBook, format).orElseThrow();
            Optional<BookVersion> current = versions.stream()
                    .filter(version -> format.equalsIgnoreCase(version.getFormat()))
                    .filter(version -> "CRAWLER".equals(version.getSourceType()))
                    .filter(version -> crawlerBook.getId().toString().equals(version.getSourceId()))
                    .max(Comparator.comparing(BookVersion::getId));
            if (current.filter(version -> Objects.equals(version.getFileHash(), export.getFileHash())).isPresent()) continue;
            Optional<BookVersion> duplicate = versionRepository.findByFileHash(export.getFileHash());
            if (duplicate.isPresent()) continue;
            publishVersion(crawlerBook, libraryBook, versions, export, format);
            versions = versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(libraryBook);
            published++;
        }
        return published;
    }

    private void publishVersion(CrawlerBook crawlerBook, Book libraryBook, List<BookVersion> versions,
            CrawlerBookExport export, String format) {
        Path source = Path.of(export.getFilePath());
        Path target = Path.of(uploadPath).resolve(UUID.randomUUID() + "." + format.toLowerCase(Locale.ROOT));
        boolean primary = format.equalsIgnoreCase(libraryBook.getFormat());
        BookVersion previousPrimary = versions.stream().filter(version -> Boolean.TRUE.equals(version.getPrimaryVersion()))
                .findFirst().orElse(null);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
            if (primary) {
                versions.stream().filter(version -> Boolean.TRUE.equals(version.getPrimaryVersion())).forEach(version -> {
                    version.setPrimaryVersion(false);
                    versionRepository.save(version);
                });
            }
            BookVersion version = versionRepository.save(BookVersion.builder().book(libraryBook)
                    .displayName(safe(crawlerBook.getBookName()) + "-" + availableChapters(crawlerBook).size()
                            + "章." + format.toLowerCase(Locale.ROOT))
                    .format(format.toLowerCase(Locale.ROOT)).filePath(target.toString())
                    .fileSize(Files.size(target)).fileHash(export.getFileHash()).primaryVersion(primary)
                    .chapterCount(availableChapters(crawlerBook).size()).sourceType("CRAWLER")
                    .sourceId(crawlerBook.getId().toString()).sourceSite(crawlerBook.getSite().getSiteCode())
                    .sourceUrl(crawlerBook.getBookUrl()).build());
            if (primary) {
                libraryBook.setTitle(crawlerBook.getBookName()); libraryBook.setAuthor(crawlerBook.getAuthor());
                libraryBook.setDescription(crawlerBook.getDescription()); libraryBook.setCoverUrl(crawlerBook.getCoverUrl());
                libraryBook.setFilePath(target.toString()); libraryBook.setFileSize(Files.size(target));
                libraryBook.setFileHash(export.getFileHash()); libraryBook.setChapterCount(version.getChapterCount());
                bookRepository.save(libraryBook);
                migrateProgress(previousPrimary, version);
            }
        } catch (Exception exception) {
            try { Files.deleteIfExists(target); } catch (Exception ignored) { }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "同步书库版本失败", exception);
        }
    }

    private void migrateProgress(BookVersion previous, BookVersion current) {
        if (previous == null) return;
        versionProgressRepository.findByUserAndVersion(previous.getBook().getUser(), previous).ifPresent(progress ->
                versionProgressRepository.save(VersionReadingProgress.builder().version(current)
                        .user(progress.getUser()).currentChapter(progress.getCurrentChapter())
                        .currentChapterTitle(progress.getCurrentChapterTitle())
                        .locator(progress.getLocator())
                        .chapterProgress(progress.getChapterProgress()).totalProgress(progress.getTotalProgress())
                        .readingTimeSeconds(progress.getReadingTimeSeconds()).lastReadAt(progress.getLastReadAt()).build()));
    }

    private List<CrawlerChapter> availableChapters(CrawlerBook book) {
        return chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book).stream()
                .filter(chapter -> chapter.getContent() != null && !chapter.getContent().isBlank())
                .toList();
    }

    private void validateHasContent(List<CrawlerChapter> chapters) {
        if (chapters.isEmpty()) throw new ResponseStatusException(
                HttpStatus.CONFLICT, "书籍还没有可用正文，不能生成或加入书库");
    }

    private void writeTxt(Path path, CrawlerBook book, List<CrawlerChapter> chapters) throws IOException {
        StringBuilder text = new StringBuilder("《").append(book.getBookName()).append("》\n\n作者：")
                .append(defaultString(book.getAuthor(), "未知作者")).append("\n\n");
        if (book.getDescription() != null && !book.getDescription().isBlank()) text.append("简介：\n").append(book.getDescription()).append("\n\n");
        for (CrawlerChapter chapter : chapters) text.append(chapter.getChapterName()).append("\n\n").append(chapter.getContent()).append("\n\n");
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private void writeEpub(Path path, CrawlerBook book, List<CrawlerChapter> chapters) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32(); crc.update(mime); ZipEntry entry = new ZipEntry("mimetype"); entry.setMethod(ZipEntry.STORED); entry.setSize(mime.length); entry.setCompressedSize(mime.length); entry.setCrc(crc.getValue()); zip.putNextEntry(entry); zip.write(mime); zip.closeEntry();
            write(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            StringBuilder nav = new StringBuilder(); StringBuilder manifest = new StringBuilder("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"); StringBuilder spine = new StringBuilder();
            for (int i = 0; i < chapters.size(); i++) {
                CrawlerChapter chapter = chapters.get(i); String token = chapterToken(chapter); String file = "chapter-" + token + ".xhtml";
                nav.append("<li><a href=\"").append(file).append("\">").append(xml(chapter.getChapterName())).append("</a></li>");
                manifest.append("<item id=\"c").append(token).append("\" href=\"").append(file).append("\" media-type=\"application/xhtml+xml\"/>"); spine.append("<itemref idref=\"c").append(token).append("\"/>");
                StringBuilder body = new StringBuilder(); for (String p : chapter.getContent().split("\\n+")) if (!p.isBlank()) body.append("<p>").append(xml(p.trim())).append("</p>");
                write(zip, "OEBPS/" + file, xhtml(chapter.getChapterName(), "<h1>" + xml(chapter.getChapterName()) + "</h1>" + body));
            }
            write(zip, "OEBPS/nav.xhtml", xhtml("目录", "<nav epub:type=\"toc\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><h1>目录</h1><ol>" + nav + "</ol></nav>"));
            String modified = java.time.format.DateTimeFormatter.ISO_INSTANT.format(
                    java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            String identifier = UUID.nameUUIDFromBytes((book.getSite().getSiteCode() + ":" + book.getExternalBookId()).getBytes(StandardCharsets.UTF_8)).toString();
            write(zip, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">urn:uuid:" + identifier + "</dc:identifier><dc:title>" + xml(book.getBookName()) + "</dc:title><dc:creator>" + xml(defaultString(book.getAuthor(), "未知作者")) + "</dc:creator><dc:language>zh-CN</dc:language><meta property=\"dcterms:modified\">" + modified + "</meta></metadata><manifest>" + manifest + "</manifest><spine>" + spine + "</spine></package>");
        }
    }

    private String chapterToken(CrawlerChapter chapter) {
        String identity = defaultString(chapter.getExternalChapterId(),
                defaultString(chapter.getChapterUrl(), "chapter-" + Objects.toString(
                        chapter.getId(), Objects.toString(chapter.getChapterIndex(), "unknown"))));
        return sha256(identity).substring(0, 20);
    }

    private String sourceHash(CrawlerBook book, List<CrawlerChapter> chapters, String format) {
        StringBuilder source = new StringBuilder(format).append('\n').append(book.getBookName()).append('\n')
                .append(defaultString(book.getAuthor(), "")).append('\n').append(defaultString(book.getDescription(), ""))
                .append('\n').append(defaultString(book.getCoverUrl(), ""));
        for (CrawlerChapter chapter : chapters) source.append('\n').append(chapter.getChapterIndex()).append('|')
                .append(defaultString(chapter.getExternalChapterId(), chapter.getChapterUrl())).append('|')
                .append(chapter.getChapterName()).append('|')
                .append(defaultString(chapter.getContentHash(), sha256(defaultString(chapter.getContent(), ""))));
        return sha256(source.toString());
    }

    private void replaceFile(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private String xhtml(String title, String body) { return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>" + xml(title) + "</title><style>body{font-family:serif;line-height:1.7;padding:1em}h1{text-align:center}p{text-indent:2em}</style></head><body>" + body + "</body></html>"; }
    private void write(ZipOutputStream zip, String name, String value) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private String hash(Path path) throws IOException { try (InputStream in = Files.newInputStream(path)) { MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int read; while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read); return HexFormat.of().formatHex(digest.digest()); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String xml(String value) { return Objects.toString(value, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private String safe(String value) { return value.replaceAll("[\\\\/:*?\"<>|]", "_"); }
    private String defaultString(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private int value(Integer value) { return value == null ? 0 : value; }
    private void recordOperation(User user, CrawlerBook book, String description, String details) {
        try {
            operationLogService.recordEntry(user, OperationLog.Action.CRAWLER_TASK,
                    book.getLibraryBook() == null ? null : book.getLibraryBook().getId(),
                    book.getBookName(), description, "采集书籍ID：" + book.getId()
                            + "；网站：" + book.getSite().getSiteName() + "；" + details);
        } catch (Exception exception) {
            log.warn("写入采集书籍发布操作日志失败: crawlerBookId={}", book.getId(), exception);
        }
    }
    private ExportView view(CrawlerBookExport e) { return new ExportView(e.getId(), e.getFormat(), e.getFileSize() == null ? 0 : e.getFileSize(), e.getFileHash(), e.getCreatedAt()); }
}
