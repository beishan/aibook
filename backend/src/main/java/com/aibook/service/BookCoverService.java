package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 本地书籍封面存储服务。
 */
@Service
@RequiredArgsConstructor
public class BookCoverService {

    private static final long MAX_COVER_SIZE = 10L * 1024 * 1024;
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final BookRepository bookRepository;

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.cover.dir:covers}")
    private String coverDir;

    @Transactional
    public Book upload(Book book, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择封面图片");
        }
        if (file.getSize() > MAX_COVER_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面图片不能超过10MB");
        }

        try {
            storeCover(book, file.getBytes(), file.getContentType(), true);
            return bookRepository.save(book);
        } catch (Exception exception) {
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "封面保存失败",
                    exception);
        }
    }

    /**
     * 保存从 EPUB 中提取的内嵌封面，不覆盖已有封面。
     *
     * @return 是否保存了新封面
     */
    public boolean storeExtractedCover(
            Book book, byte[] imageBytes, String declaredContentType) {
        return storeCover(book, imageBytes, declaredContentType, false);
    }

    private boolean storeCover(
            Book book,
            byte[] imageBytes,
            String declaredContentType,
            boolean overwrite) {
        if (!overwrite && book.getCoverUrl() != null && !book.getCoverUrl().isBlank()) {
            return false;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面图片内容为空");
        }
        if (imageBytes.length > MAX_COVER_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面图片不能超过10MB");
        }

        String contentType = detectContentType(imageBytes);
        if (contentType == null
                && declaredContentType != null
                && SUPPORTED_TYPES.containsKey(declaredContentType)) {
            contentType = declaredContentType;
        }
        String extension = SUPPORTED_TYPES.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP 或 GIF 图片");
        }

        try {
            Path directory = Paths.get(uploadDir, coverDir);
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + extension;
            Files.write(directory.resolve(filename), imageBytes);
            book.setCoverUrl(coverDir + "/" + filename);
            return true;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "封面保存失败",
                    exception);
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
