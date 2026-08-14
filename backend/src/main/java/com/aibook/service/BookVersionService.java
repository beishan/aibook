package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.ReadingProgress;
import com.aibook.model.entity.VersionReadingProgress;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.ReadingProgressRepository;
import com.aibook.repository.VersionReadingProgressRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookVersionService {

    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "txt", "epub", "pdf", "mobi", "azw3", "docx", "doc",
            "html", "htm", "md", "cbz", "cbr");

    private final BookVersionRepository bookVersionRepository;
    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final VersionReadingProgressRepository versionProgressRepository;
    private final TxtParserService txtParserService;
    private final ObjectMapper objectMapper;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Transactional
    public List<com.aibook.dto.BookVersionDTO> getVersions(Book book) {
        ensurePrimaryVersion(book);
        return bookVersionRepository
                .findByBookOrderByPrimaryVersionDescCreatedAtAsc(book)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public BookVersion resolveVersion(Book book, Long versionId) {
        if (versionId == null) {
            return ensurePrimaryVersion(book);
        }
        BookVersion version = bookVersionRepository.findByIdAndBook(versionId, book)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "书籍版本不存在"));
        return Boolean.TRUE.equals(version.getPrimaryVersion())
                ? syncPrimaryVersion(book, version)
                : version;
    }

    @Transactional
    public BookVersion ensurePrimaryVersion(Book book) {
        return ensurePrimaryVersion(book, null);
    }

    @Transactional
    public BookVersion ensurePrimaryVersion(Book book, String uploadedFilename) {
        BookVersion version = bookVersionRepository.findByBookAndPrimaryVersionTrue(book)
                .map(existing -> {
                    if (isGeneratedStorageFilename(existing.getDisplayName())
                            && uploadedFilename != null && !uploadedFilename.isBlank()) {
                        existing.setDisplayName(safeFilename(uploadedFilename));
                    }
                    return syncPrimaryVersion(book, existing);
                })
                .orElseGet(() -> {
                    String displayName = resolvePrimaryDisplayName(book, uploadedFilename);
                    BookVersion createdVersion = bookVersionRepository.save(BookVersion.builder()
                            .book(book)
                            .displayName(displayName)
                            .format(book.getFormat())
                            .filePath(book.getFilePath())
                            .fileSize(book.getFileSize())
                            .fileHash(book.getFileHash())
                            .primaryVersion(true)
                            .chapterInfo(book.getChapterInfo())
                            .chapterCount(book.getChapterCount())
                            .build());
                    migrateLegacyProgress(book, createdVersion);
                    return createdVersion;
                });
        return version;
    }

    @Transactional
    public com.aibook.dto.BookVersionDTO addVersion(Book book, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名不能为空");
        }
        String format = extension(originalName);
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的书籍格式");
        }

        ensurePrimaryVersion(book);
        Path uploadDirectory = Paths.get(uploadPath);
        Path target = uploadDirectory.resolve(UUID.randomUUID() + "." + format);
        try {
            Files.createDirectories(uploadDirectory);
            file.transferTo(target);
            String hash = calculateHash(target);
            if (bookRepository.findByFileHash(hash).isPresent()
                    || bookVersionRepository.findByFileHash(hash).isPresent()) {
                Files.deleteIfExists(target);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该文件版本已存在");
            }

            String chapterInfo = null;
            Integer chapterCount = null;
            if ("txt".equals(format) || "md".equals(format)) {
                chapterInfo = txtParserService.parseChapters(target);
                chapterCount = objectMapper.readValue(
                        chapterInfo, new TypeReference<List<Object>>() {}).size();
            }

            BookVersion version = bookVersionRepository.save(BookVersion.builder()
                    .book(book)
                    .displayName(Paths.get(originalName).getFileName().toString())
                    .format(format)
                    .filePath(target.toString())
                    .fileSize(Files.size(target))
                    .fileHash(hash)
                    .primaryVersion(false)
                    .chapterInfo(chapterInfo)
                    .chapterCount(chapterCount)
                    .build());
            return toDTO(version);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
                // 忽略临时上传文件清理失败，保留原始异常。
            }
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "版本上传失败: " + exception.getMessage(),
                    exception);
        }
    }

    /** 将服务端已经生成的文件复制为书籍新版本。源文件始终保留。 */
    @Transactional
    public com.aibook.dto.BookVersionDTO addGeneratedVersion(
            Book book, Path generatedFile, String displayName) {
        ensurePrimaryVersion(book);
        Path uploadDirectory = Paths.get(uploadPath);
        Path target = uploadDirectory.resolve(UUID.randomUUID() + ".epub");
        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(generatedFile, target);
            String hash = calculateHash(target);
            if (bookRepository.findByFileHash(hash).isPresent()
                    || bookVersionRepository.findByFileHash(hash).isPresent()) {
                Files.deleteIfExists(target);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该 EPUB 版本已存在");
            }
            BookVersion version = bookVersionRepository.save(BookVersion.builder()
                    .book(book)
                    .displayName(safeFilename(displayName))
                    .format("epub")
                    .filePath(target.toString())
                    .fileSize(Files.size(target))
                    .fileHash(hash)
                    .primaryVersion(false)
                    .build());
            return toDTO(version);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            try { Files.deleteIfExists(target); } catch (Exception ignored) { }
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "转换结果关联失败", exception);
        }
    }

    @Transactional
    public void deleteVersion(Book book, Long versionId) {
        BookVersion version = resolveVersion(book, versionId);
        if (Boolean.TRUE.equals(version.getPrimaryVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原始版本不能删除");
        }
        versionProgressRepository.deleteByVersion(version);
        bookVersionRepository.delete(version);
    }

    public Book toReadableBook(Book book, BookVersion version) {
        return Book.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .format(version.getFormat())
                .filePath(version.getFilePath())
                .fileSize(version.getFileSize())
                .fileHash(version.getFileHash())
                .chapterInfo(version.getChapterInfo())
                .chapterCount(version.getChapterCount())
                .user(book.getUser())
                .build();
    }

    public com.aibook.dto.BookVersionDTO toDTO(BookVersion version) {
        return com.aibook.dto.BookVersionDTO.builder()
                .id(version.getId())
                .displayName(version.getDisplayName())
                .format(version.getFormat())
                .fileSize(version.getFileSize())
                .fileHash(version.getFileHash())
                .primaryVersion(version.getPrimaryVersion())
                .chapterCount(version.getChapterCount())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private void migrateLegacyProgress(Book book, BookVersion version) {
        for (ReadingProgress legacy : readingProgressRepository.findAllByBook(book)) {
            if (versionProgressRepository.existsByUserAndVersion(legacy.getUser(), version)) {
                continue;
            }
            versionProgressRepository.save(VersionReadingProgress.builder()
                    .version(version)
                    .user(legacy.getUser())
                    .currentChapter(legacy.getCurrentChapter())
                    .currentChapterTitle(legacy.getCurrentChapterTitle())
                    .chapterProgress(legacy.getChapterProgress())
                    .totalProgress(legacy.getTotalProgress())
                    .readingTimeSeconds(legacy.getReadingTimeSeconds())
                    .lastReadAt(legacy.getLastReadAt())
                    .build());
        }
    }

    private BookVersion syncPrimaryVersion(Book book, BookVersion version) {
        version.setFormat(book.getFormat());
        version.setFilePath(book.getFilePath());
        version.setFileSize(book.getFileSize());
        version.setFileHash(book.getFileHash());
        version.setChapterInfo(book.getChapterInfo());
        version.setChapterCount(book.getChapterCount());
        return bookVersionRepository.save(version);
    }

    private String resolvePrimaryDisplayName(Book book, String uploadedFilename) {
        if (uploadedFilename != null && !uploadedFilename.isBlank()) {
            return safeFilename(uploadedFilename);
        }
        String storedFilename = Paths.get(book.getFilePath()).getFileName().toString();
        if (!isGeneratedStorageFilename(storedFilename)) {
            return storedFilename;
        }
        String title = book.getTitle() == null || book.getTitle().isBlank()
                ? "未命名书籍"
                : book.getTitle().trim();
        return title + "." + book.getFormat().toLowerCase(Locale.ROOT);
    }

    private String safeFilename(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private boolean isGeneratedStorageFilename(String filename) {
        if (filename == null || filename.isBlank()) return false;
        String safeName = safeFilename(filename);
        int dot = safeName.lastIndexOf('.');
        String baseName = dot > 0 ? safeName.substring(0, dot) : safeName;
        return BookParsingService.isGeneratedStorageTitle(baseName);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件缺少扩展名");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String calculateHash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
