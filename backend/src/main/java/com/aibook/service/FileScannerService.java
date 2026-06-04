package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件扫描服务
 * 扫描配置的目录，发现并导入书籍文件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileScannerService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final MetadataService metadataService;
    private final TxtParserService txtParserService;

    @Value("#{'${scanning.directories:/books/fiction,/books/tech}'.split(',')}")
    private List<String> scanDirectories;

    @Value("${scanning.enabled:true}")
    private boolean scanningEnabled;

    // 支持的文件格式
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
        "txt", "epub", "mobi", "azw3", "pdf", "docx", "doc",
        "html", "htm", "cbz", "cbr", "md"
    );

    /**
     * 手动触发扫描
     */
    public ScanResult scan(User user) {
        ScanResult result = new ScanResult();
        result.setStartTime(System.currentTimeMillis());

        for (String dirPath : scanDirectories) {
            Path dir = Paths.get(dirPath);
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try {
                    scanDirectory(dir, user, result);
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
    public ScanResult scanDirectory(String dirPath) {
        ScanResult result = new ScanResult();
        result.setStartTime(System.currentTimeMillis());

        Path dir = Paths.get(dirPath);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                // 获取第一个用户作为默认用户
                User defaultUser = userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("没有用户，请先注册"));
                scanDirectory(dir, defaultUser, result);
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
     * 定时扫描（每天凌晨2点）
     */
    @Scheduled(cron = "${scanning.cron:0 0 2 * * ?}")
    public void scheduledScan() {
        if (!scanningEnabled) {
            return;
        }
        log.info("开始定时扫描...");
        // TODO: 需要获取默认用户或遍历所有用户
    }

    /**
     * 扫描单个目录
     */
    private void scanDirectory(Path dir, User user, ScanResult result) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFormat)
                .collect(Collectors.toList());

            for (Path file : files) {
                try {
                    processFile(file, user, result);
                } catch (Exception e) {
                    log.error("处理文件失败: {}", file, e);
                    result.addFailed(file.toString(), e.getMessage());
                }
            }
        }
    }

    /**
     * 处理单个文件
     */
    private void processFile(Path file, User user, ScanResult result) {
        try {
            String fileHash = calculateFileHash(file);
            String format = getFileFormat(file);

            // 检查是否已存在
            Optional<Book> existingBook = bookRepository.findByFileHash(fileHash);
            if (existingBook.isPresent()) {
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
                .user(user)
                .build();

            // 尝试提取元数据
            extractMetadata(file, book);

            bookRepository.save(book);
            result.addNew(file.toString());
            log.info("成功导入书籍: {}", book.getTitle());

        } catch (Exception e) {
            log.error("处理文件失败: {}", file, e);
            result.addFailed(file.toString(), e.getMessage());
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
                        log.info("TXT章节解析成功: {}，章节数: {}", file,
                                chapterInfo.split("\"title\"").length - 1);
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
        // TODO: 使用 EPUB 解析库提取元数据
        // 这里先留空，后续可以集成 epublib 或类似库
        log.debug("EPUB 元数据提取功能待实现: {}", file);
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
        byte[] fileBytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(fileBytes);

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
        private long startTime;
        private long endTime;
        private List<String> newBooks = new ArrayList<>();
        private List<String> skippedBooks = new ArrayList<>();
        private List<Map<String, String>> failedBooks = new ArrayList<>();
        private List<Map<String, String>> errors = new ArrayList<>();

        public void addNew(String path) {
            newBooks.add(path);
        }

        public void addSkipped(String path) {
            skippedBooks.add(path);
        }

        public void addFailed(String path, String reason) {
            Map<String, String> failed = new HashMap<>();
            failed.put("path", path);
            failed.put("reason", reason);
            failedBooks.add(failed);
        }

        public void addError(String path, String message) {
            Map<String, String> error = new HashMap<>();
            error.put("path", path);
            error.put("message", message);
            errors.add(error);
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
