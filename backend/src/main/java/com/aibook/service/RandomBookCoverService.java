package com.aibook.service;

import com.aibook.dto.RandomBookCoverDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.RandomBookCover;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.RandomBookCoverRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 管理用户封面库，并将其中的图片随机复制为书籍独立封面。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RandomBookCoverService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final RandomBookCoverRepository coverRepository;
    private final BookRepository bookRepository;

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.cover.dir:covers}")
    private String coverDir;

    @Transactional(readOnly = true)
    public List<RandomBookCoverDTO> list(User user) {
        return coverRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<RandomBookCoverDTO> upload(User user, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择封面图片");
        }
        return files.stream().map(file -> uploadOne(user, file)).map(this::toDto).toList();
    }

    @Transactional
    public void delete(User user, Long id) {
        RandomBookCover cover = coverRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "封面不存在"));
        coverRepository.delete(cover);
        deleteQuietly(libraryPath(cover));
    }

    /** 仅当书籍没有封面时尝试自动随机；封面库为空时安静跳过。 */
    @Transactional
    public Book assignIfMissing(Book book, User user) {
        if (book.getCoverUrl() != null && !book.getCoverUrl().isBlank()) return book;
        try {
            return assign(book, user, false);
        } catch (Exception exception) {
            log.warn("自动分配随机封面失败，保留书籍无封面状态: bookId={}", book.getId(), exception);
            return book;
        }
    }

    /** 手动为书籍随机封面；封面库为空时返回明确错误。 */
    @Transactional
    public Book assign(Book book, User user) {
        return assign(book, user, true);
    }

    private Book assign(Book book, User user, boolean failWhenEmpty) {
        List<RandomBookCover> covers = coverRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .filter(cover -> Files.isRegularFile(libraryPath(cover)))
                .toList();
        if (covers.isEmpty()) {
            if (failWhenEmpty) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "封面库为空，请先在设置中添加随机书籍封面");
            }
            return book;
        }

        RandomBookCover selected = covers.get(ThreadLocalRandom.current().nextInt(covers.size()));
        Path source = libraryPath(selected);

        Path target = null;
        String previousCoverUrl = book.getCoverUrl();
        try {
            Files.createDirectories(coverDirectory());
            String extension = SUPPORTED_TYPES.get(selected.getContentType());
            String filename = "random-book-" + UUID.randomUUID() + extension;
            target = coverDirectory().resolve(filename);
            Files.copy(source, target);
            book.setCoverUrl(coverDir + "/" + filename);
            return bookRepository.save(book);
        } catch (Exception exception) {
            book.setCoverUrl(previousCoverUrl);
            deleteQuietly(target);
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "随机封面分配失败", exception);
        }
    }

    private RandomBookCover uploadOne(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面图片不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单张封面图片不能超过10MB");
        }

        Path storedFile = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            String extension = SUPPORTED_TYPES.get(contentType);
            if (extension == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP 或 GIF 图片");
            }
            Files.createDirectories(coverDirectory());
            String filename = "library-" + user.getId() + "-" + UUID.randomUUID() + extension;
            storedFile = coverDirectory().resolve(filename);
            Files.write(storedFile, bytes);
            return coverRepository.save(RandomBookCover.builder()
                    .user(user)
                    .originalFilename(safeFilename(file.getOriginalFilename()))
                    .storedFilename(filename)
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .build());
        } catch (ResponseStatusException exception) {
            deleteQuietly(storedFile);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(storedFile);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "封面库图片保存失败", exception);
        }
    }

    private RandomBookCoverDTO toDto(RandomBookCover cover) {
        return new RandomBookCoverDTO(
                cover.getId(),
                cover.getOriginalFilename(),
                coverDir + "/" + cover.getStoredFilename(),
                cover.getContentType(),
                cover.getFileSize(),
                cover.getCreatedAt());
    }

    private Path coverDirectory() {
        return Paths.get(uploadDir, coverDir).toAbsolutePath().normalize();
    }

    private Path libraryPath(RandomBookCover cover) {
        Path directory = coverDirectory();
        Path path = directory.resolve(cover.getStoredFilename()).normalize();
        if (!path.startsWith(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面文件路径无效");
        }
        return path;
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return "未命名封面";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return (slash >= 0 ? normalized.substring(slash + 1) : normalized).trim();
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 数据库操作优先完成；残留文件可由后续存储清理处理。
        }
    }
}
