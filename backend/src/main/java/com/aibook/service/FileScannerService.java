package com.aibook.service;

import com.aibook.config.ScanSettings;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件扫描服务
 * 扫描配置的目录，发现并导入书籍文件
 */
@Service
@Slf4j
public class FileScannerService {

    private final BookRepository bookRepository;
    private final BookVersionRepository bookVersionRepository;
    private final ScannedBookPersistenceService scannedBookPersistenceService;
    private final MetadataService metadataService;
    private final TxtParserService txtParserService;
    private final BookParsingService bookParsingService;

    @Autowired
    public FileScannerService(
            BookRepository bookRepository,
            BookVersionRepository bookVersionRepository,
            ScannedBookPersistenceService scannedBookPersistenceService,
            MetadataService metadataService,
            TxtParserService txtParserService,
            BookParsingService bookParsingService) {
        this.bookRepository = bookRepository;
        this.bookVersionRepository = bookVersionRepository;
        this.scannedBookPersistenceService = scannedBookPersistenceService;
        this.metadataService = metadataService;
        this.txtParserService = txtParserService;
        this.bookParsingService = bookParsingService;
    }

    FileScannerService(
            BookRepository bookRepository,
            ScannedBookPersistenceService scannedBookPersistenceService,
            MetadataService metadataService,
            TxtParserService txtParserService,
            BookParsingService bookParsingService) {
        this(
                bookRepository,
                null,
                scannedBookPersistenceService,
                metadataService,
                txtParserService,
                bookParsingService);
    }

    @Value("#{'${scanning.directories:/books/fiction,/books/tech}'.split(',')}")
    private List<String> scanDirectories;

    // 支持的文件格式
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
        "txt", "epub", "mobi", "azw3", "pdf", "docx", "doc",
        "html", "htm", "cbz", "cbr", "md"
    );
    private static final AtomicInteger SCAN_POOL_SEQUENCE = new AtomicInteger();

    /**
     * 手动触发扫描
     */
    public ScanResult scan(User user) {
        ScanResult result = new ScanResult();
        result.setStartTime(System.currentTimeMillis());
        Long userId = Objects.requireNonNull(user.getId(), "扫描用户 ID 不能为空");
        int threadCount = ScanSettings.normalizeThreadCount(user.getScanThreadCount());

        for (String dirPath : scanDirectories) {
            Path dir = Paths.get(dirPath);
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try {
                    scanDirectory(dir, userId, null, null, threadCount, result);
                } catch (IOException e) {
                    log.error("扫描目录失败: {}", dirPath, e);
                    result.addError(dirPath, e.getMessage());
                }
            } else {
                log.warn("目录不存在: {}", dirPath);
                result.addError(dirPath, "目录不存在");
            }
        }

        result.setEndTime(System.currentTimeMillis());
        return result;
    }

    /**
     * 扫描指定目录（供 ScanDirectoryService 调用）
     */
    public ScanResult scanDirectory(String dirPath, User user) {
        return scanDirectory(dirPath, user, null);
    }

    /**
     * 扫描指定目录，并为新导入书籍设置默认分类。
     */
    public ScanResult scanDirectory(String dirPath, User user, Long defaultCategoryId) {
        return scanDirectory(dirPath, user, defaultCategoryId, null);
    }

    /** 扫描受管目录，并把每本发现的书关联到该目录来源。 */
    public ScanResult scanDirectory(
            String dirPath, User user, Long defaultCategoryId, Long directoryId) {
        return scanDirectory(
                dirPath,
                Objects.requireNonNull(user.getId(), "扫描用户 ID 不能为空"),
                ScanSettings.normalizeThreadCount(user.getScanThreadCount()),
                defaultCategoryId,
                directoryId,
                new ScanResult());
    }

    /**
     * 使用给定的实时结果对象扫描目录，供异步扫描任务查询进度。
     */
    public ScanResult scanDirectory(
            String dirPath,
            Long userId,
            int threadCount,
            Long defaultCategoryId,
            ScanResult result) {
        return scanDirectory(dirPath, userId, threadCount, defaultCategoryId, null, result);
    }

    public ScanResult scanDirectory(
            String dirPath,
            Long userId,
            int threadCount,
            Long defaultCategoryId,
            Long directoryId,
            ScanResult result) {
        result.setStartTime(System.currentTimeMillis());
        threadCount = ScanSettings.normalizeThreadCount(threadCount);

        Path dir = Paths.get(dirPath);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                scanDirectory(
                        dir,
                        userId,
                        defaultCategoryId,
                        directoryId,
                        threadCount,
                        result);
            } catch (IOException e) {
                log.error("扫描目录失败: {}", dirPath, e);
                result.addError(dirPath, e.getMessage());
            }
        } else {
            log.warn("目录不存在: {}", dirPath);
            result.addError(dirPath, "目录不存在");
        }

        result.setEndTime(System.currentTimeMillis());
        return result;
    }

    /**
     * 扫描单个目录
     */
    private void scanDirectory(
            Path dir,
            Long userId,
            Long defaultCategoryId,
            Long directoryId,
            int threadCount,
            ScanResult result) throws IOException {
        result.setThreadCount(threadCount);

        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFormat)
                .collect(Collectors.toList());
            result.setTotalCount(files.size());

            if (files.isEmpty()) {
                return;
            }

            Set<String> claimedHashes = ConcurrentHashMap.newKeySet();
            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(threadCount, files.size()),
                    runnable -> {
                        Thread thread = new Thread(
                                runnable,
                                "book-scan-" + SCAN_POOL_SEQUENCE.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });

            try {
                for (Path file : files) {
                    executor.submit(
                            () -> processFile(
                                    file,
                                    userId,
                                    defaultCategoryId,
                                    directoryId,
                                    claimedHashes,
                                    result));
                }
            } finally {
                executor.shutdown();
                awaitScanCompletion(executor);
            }
        }
    }

    private void awaitScanCompletion(ExecutorService executor) {
        try {
            while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log.info("目录扫描仍在进行中...");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("目录扫描被中断", e);
        }
    }

    /**
     * 处理单个文件
     */
    private void processFile(
            Path file,
            Long userId,
            Long defaultCategoryId,
            Long directoryId,
            Set<String> claimedHashes,
            ScanResult result) {
        String claimedHash = null;
        try {
            String fileHash = calculateFileHash(file);
            String format = getFileFormat(file);

            if (!claimedHashes.add(fileHash)) {
                if (directoryId != null) {
                    scannedBookPersistenceService.recordExistingSource(
                            fileHash, userId, directoryId);
                }
                result.addSkipped(file.toString());
                return;
            }
            claimedHash = fileHash;

            // 检查是否已存在
            Optional<Book> existingBook = bookRepository.findByFileHash(fileHash);
            Optional<com.aibook.model.entity.BookVersion> existingVersion = bookVersionRepository == null
                    ? Optional.empty()
                    : bookVersionRepository.findByFileHash(fileHash);
            if (existingBook.isPresent() || existingVersion.isPresent()) {
                if (directoryId != null) {
                    existingBook.ifPresent(book -> scannedBookPersistenceService.recordExistingSource(
                            book, userId, directoryId));
                    existingVersion.ifPresent(version -> scannedBookPersistenceService.recordExistingSource(
                            version.getBook(), userId, directoryId));
                }
                result.addSkipped(file.toString());
                return;
            }

            // 创建书籍记录
            Book book = Book.builder()
                .title(extractTitle(file.getFileName().toString()))
                .format(format)
                .filePath(file.toString())
                .fileSize(Files.size(file))
                .fileHash(fileHash)
                .build();

            // 尝试提取元数据
            extractMetadata(file, book);

            if (directoryId == null) {
                scannedBookPersistenceService.save(book, userId, defaultCategoryId);
            } else {
                scannedBookPersistenceService.save(book, userId, defaultCategoryId, directoryId);
            }
            result.addNew(file.toString());
            log.info("成功导入书籍: {}", book.getTitle());

        } catch (Exception e) {
            if (claimedHash != null) {
                claimedHashes.remove(claimedHash);
            }
            log.error("处理文件失败: {}", file, e);
            result.addFailed(file.toString(), e.getMessage());
        } finally {
            result.markScanned(file.toString());
        }
    }

    /**
     * 提取文件元数据
     */
    private void extractMetadata(Path file, Book book) {
        String format = book.getFormat();

        try {
            switch (format) {
                case "epub":
                    extractEpubMetadata(file, book);
                    break;
                case "pdf":
                    extractPdfMetadata(file, book);
                    break;
                case "txt":
                case "md":
                    try {
                        String chapterInfo = txtParserService.parseChapters(file);
                        book.setChapterInfo(chapterInfo);
                        int chapterCount = chapterInfo.split("\"title\"").length - 1;
                        book.setChapterCount(chapterCount);
                        log.info("TXT章节解析成功: {}，章节数: {}", file, chapterCount);
                    } catch (Exception e) {
                        log.warn("TXT章节解析失败: {}", file, e);
                    }
                    break;
                default:
                    log.debug("格式 {} 暂不支持元数据提取", format);
            }
        } catch (Exception e) {
            log.warn("提取元数据失败: {}", file, e);
        }
    }

    /**
     * 提取 EPUB 元数据
     */
    private void extractEpubMetadata(Path file, Book book) throws IOException {
        try {
            bookParsingService.parseEpubMetadata(book, file, new ArrayList<>());
        } catch (Exception exception) {
            throw new IOException("EPUB 元数据提取失败", exception);
        }
    }

    /**
     * 提取 PDF 元数据
     */
    private void extractPdfMetadata(Path file, Book book) throws IOException {
        // TODO: 使用 PDFBox 提取元数据
        // 这里先留空，后续可以集成 PDFBox
        log.debug("PDF 元数据提取功能待实现: {}", file);
    }

    /**
     * 计算文件哈希（SHA-256）
     */
    private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hashBytes = digest.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 检查文件格式是否支持
     */
    private boolean isSupportedFormat(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        String format = getFileFormat(file);
        return SUPPORTED_FORMATS.contains(format);
    }

    /**
     * 获取文件格式
     */
    private String getFileFormat(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * 从文件名提取标题
     */
    private String extractTitle(String fileName) {
        // 移除扩展名
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }

    /**
     * 扫描结果
     */
    @lombok.Data
    public static class ScanResult {
        private volatile long startTime;
        private volatile long endTime;
        private volatile int threadCount = ScanSettings.DEFAULT_THREAD_COUNT;
        private volatile int totalCount;
        private volatile String currentFile;
        private final AtomicInteger scannedCount = new AtomicInteger();
        private final List<String> newBooks =
                Collections.synchronizedList(new ArrayList<>());
        private final List<String> skippedBooks =
                Collections.synchronizedList(new ArrayList<>());
        private final List<Map<String, String>> failedBooks =
                Collections.synchronizedList(new ArrayList<>());
        private final List<Map<String, String>> errors =
                Collections.synchronizedList(new ArrayList<>());

        public void addNew(String path) {
            newBooks.add(path);
        }

        public void addSkipped(String path) {
            skippedBooks.add(path);
        }

        public void addFailed(String path, String reason) {
            failedBooks.add(Map.of(
                    "path", path,
                    "reason", Objects.toString(reason, "未知错误")));
        }

        public void addError(String path, String message) {
            errors.add(Map.of(
                    "path", path,
                    "message", Objects.toString(message, "未知错误")));
        }

        public void markScanned(String path) {
            currentFile = path;
            scannedCount.incrementAndGet();
        }

        public int getScannedCount() {
            return scannedCount.get();
        }

        public int getProgressPercent() {
            if (totalCount == 0) {
                return endTime > 0 ? 100 : 0;
            }
            return Math.min(100, (int) Math.round(
                    getScannedCount() * 100.0 / totalCount));
        }

        public long getDuration() {
            return endTime - startTime;
        }

        public int getNewCount() {
            return newBooks.size();
        }

        public int getSkippedCount() {
            return skippedBooks.size();
        }

        public int getFailedCount() {
            return failedBooks.size();
        }
    }
}
