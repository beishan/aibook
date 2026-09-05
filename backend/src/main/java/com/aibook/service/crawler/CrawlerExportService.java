package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerDtos.ExportView;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import lombok.RequiredArgsConstructor;
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
public class CrawlerExportService {
    private final CrawlerManagementService managementService;
    private final CrawlerChapterRepository chapterRepository;
    private final CrawlerBookExportRepository exportRepository;
    private final CrawlerBookRepository crawlerBookRepository;
    private final BookRepository bookRepository;
    private final BookVersionRepository versionRepository;

    @Value("${crawler.storage-path:./crawler-data}") private String storagePath;
    @Value("${upload.path:./uploads}") private String uploadPath;

    @Transactional
    public List<ExportView> generate(User user, Long bookId, List<String> formats) {
        CrawlerBook book = managementService.ownedBook(user, bookId);
        validateComplete(book);
        List<CrawlerChapter> chapters = chapterRepository.findByCrawlerBookOrderByChapterIndexAsc(book);
        List<ExportView> result = new ArrayList<>();
        for (String requested : new LinkedHashSet<>(formats)) {
            String format = requested.toUpperCase(Locale.ROOT);
            if (!Set.of("TXT", "EPUB").contains(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持生成 TXT 或 EPUB");
            Path output = Path.of(storagePath).resolve("exports").resolve(book.getId() + "." + format.toLowerCase(Locale.ROOT));
            try {
                Files.createDirectories(output.getParent());
                if ("TXT".equals(format)) writeTxt(output, book, chapters); else writeEpub(output, book, chapters);
                CrawlerBookExport export = exportRepository.findByCrawlerBookAndFormat(book, format).orElseGet(CrawlerBookExport::new);
                export.setCrawlerBook(book); export.setFormat(format); export.setFilePath(output.toString()); export.setFileSize(Files.size(output)); export.setFileHash(hash(output));
                result.add(view(exportRepository.save(export)));
            } catch (IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成 " + format + " 失败", exception); }
        }
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
    public Long importLibrary(User user, Long bookId, String preferredFormat) {
        CrawlerBook crawlerBook = managementService.ownedBook(user, bookId);
        validateComplete(crawlerBook);
        String requestedFormat = preferredFormat == null ? "EPUB" : preferredFormat.toUpperCase(Locale.ROOT);
        if ("BOTH".equals(requestedFormat)) {
            Long libraryId = crawlerBook.getLibraryBook() == null
                    ? importLibrary(user, bookId, "EPUB") : crawlerBook.getLibraryBook().getId();
            CrawlerBook refreshed = managementService.ownedBook(user, bookId);
            addSecondaryVersion(refreshed, "TXT");
            return libraryId;
        }
        if (crawlerBook.getLibraryBook() != null) return crawlerBook.getLibraryBook().getId();
        String format = requestedFormat;
        CrawlerBookExport source = exportRepository.findByCrawlerBookAndFormat(crawlerBook, format)
                .orElseGet(() -> { generate(user, bookId, List.of(format)); return exportRepository.findByCrawlerBookAndFormat(crawlerBook, format).orElseThrow(); });
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
                    .chapterCount(crawlerBook.getChapterCount()).build());
            versionRepository.save(BookVersion.builder().book(book).displayName(safe(crawlerBook.getBookName()) + "." + format.toLowerCase(Locale.ROOT))
                    .format(format.toLowerCase(Locale.ROOT)).filePath(target.toString()).fileSize(Files.size(target)).fileHash(fileHash)
                    .primaryVersion(true).chapterCount(crawlerBook.getChapterCount()).sourceType("CRAWLER")
                    .sourceId(crawlerBook.getId().toString()).sourceSite(crawlerBook.getSite().getSiteCode()).sourceUrl(crawlerBook.getBookUrl()).build());
            crawlerBook.setLibraryBook(book); crawlerBook.setImportStatus(CrawlerBook.ImportStatus.IMPORTED); crawlerBookRepository.save(crawlerBook);
            return book.getId();
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { try { Files.deleteIfExists(target); } catch (Exception ignored) { } throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "加入书库失败", exception); }
    }

    private void addSecondaryVersion(CrawlerBook crawlerBook, String format) {
        Book book = crawlerBook.getLibraryBook();
        boolean exists = versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(book).stream()
                .anyMatch(version -> format.equalsIgnoreCase(version.getFormat())
                        && "CRAWLER".equals(version.getSourceType())
                        && crawlerBook.getId().toString().equals(version.getSourceId()));
        if (exists) return;
        CrawlerBookExport source = exportRepository.findByCrawlerBookAndFormat(crawlerBook, format)
                .orElseGet(() -> { generate(crawlerBook.getSite().getUser(), crawlerBook.getId(), List.of(format)); return exportRepository.findByCrawlerBookAndFormat(crawlerBook, format).orElseThrow(); });
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
                    .fileHash(fileHash).primaryVersion(false).chapterCount(crawlerBook.getChapterCount())
                    .sourceType("CRAWLER").sourceId(crawlerBook.getId().toString())
                    .sourceSite(crawlerBook.getSite().getSiteCode()).sourceUrl(crawlerBook.getBookUrl()).build());
        } catch (Exception exception) {
            try { Files.deleteIfExists(target); } catch (Exception ignored) { }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "添加 " + format + " 版本失败", exception);
        }
    }

    private void validateComplete(CrawlerBook book) {
        if (book.getCrawlStatus() != CrawlerBook.CrawlStatus.COMPLETED || value(book.getFailedChapterCount()) > 0 || value(book.getChapterCount()) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "书籍尚未完整采集，不能生成或加入书库");
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
                String file = String.format(Locale.ROOT, "chapter-%04d.xhtml", i + 1); CrawlerChapter chapter = chapters.get(i);
                nav.append("<li><a href=\"").append(file).append("\">").append(xml(chapter.getChapterName())).append("</a></li>");
                manifest.append("<item id=\"c").append(i).append("\" href=\"").append(file).append("\" media-type=\"application/xhtml+xml\"/>"); spine.append("<itemref idref=\"c").append(i).append("\"/>");
                StringBuilder body = new StringBuilder(); for (String p : chapter.getContent().split("\\n+")) if (!p.isBlank()) body.append("<p>").append(xml(p.trim())).append("</p>");
                write(zip, "OEBPS/" + file, xhtml(chapter.getChapterName(), "<h1>" + xml(chapter.getChapterName()) + "</h1>" + body));
            }
            write(zip, "OEBPS/nav.xhtml", xhtml("目录", "<nav epub:type=\"toc\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><h1>目录</h1><ol>" + nav + "</ol></nav>"));
            String modified = java.time.format.DateTimeFormatter.ISO_INSTANT.format(
                    java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            write(zip, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">urn:uuid:" + UUID.randomUUID() + "</dc:identifier><dc:title>" + xml(book.getBookName()) + "</dc:title><dc:creator>" + xml(defaultString(book.getAuthor(), "未知作者")) + "</dc:creator><dc:language>zh-CN</dc:language><meta property=\"dcterms:modified\">" + modified + "</meta></metadata><manifest>" + manifest + "</manifest><spine>" + spine + "</spine></package>");
        }
    }

    private String xhtml(String title, String body) { return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>" + xml(title) + "</title><style>body{font-family:serif;line-height:1.7;padding:1em}h1{text-align:center}p{text-indent:2em}</style></head><body>" + body + "</body></html>"; }
    private void write(ZipOutputStream zip, String name, String value) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private String hash(Path path) throws IOException { try (InputStream in = Files.newInputStream(path)) { MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int read; while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read); return HexFormat.of().formatHex(digest.digest()); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String xml(String value) { return Objects.toString(value, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private String safe(String value) { return value.replaceAll("[\\\\/:*?\"<>|]", "_"); }
    private String defaultString(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private int value(Integer value) { return value == null ? 0 : value; }
    private ExportView view(CrawlerBookExport e) { return new ExportView(e.getId(), e.getFormat(), e.getFileSize() == null ? 0 : e.getFileSize(), e.getFileHash(), e.getCreatedAt()); }
}
