package com.aibook.service;

import com.aibook.dto.ReaderBackgroundDTO;
import com.aibook.model.entity.ReaderBackground;
import com.aibook.model.entity.User;
import com.aibook.repository.ReaderBackgroundRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 管理按用户隔离的阅读背景图片。 */
@Service
@RequiredArgsConstructor
public class ReaderBackgroundService {

    private static final long MAX_FILE_SIZE = 15L * 1024 * 1024;
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final ReaderBackgroundRepository backgroundRepository;

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.cover.dir:covers}")
    private String coverDir;

    @Transactional(readOnly = true)
    public List<ReaderBackgroundDTO> list(User user) {
        return backgroundRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<ReaderBackgroundDTO> upload(User user, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择阅读背景图片");
        }
        return files.stream().map(file -> uploadOne(user, file)).map(this::toDto).toList();
    }

    @Transactional
    public void delete(User user, Long id) {
        ReaderBackground background = backgroundRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "阅读背景不存在"));
        backgroundRepository.delete(background);
        deleteQuietly(backgroundPath(background));
    }

    private ReaderBackground uploadOne(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "阅读背景图片不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "单张阅读背景图片不能超过15MB");
        }

        Path storedFile = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            String extension = contentType == null ? null : SUPPORTED_TYPES.get(contentType);
            if (extension == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP 或 GIF 图片");
            }
            Files.createDirectories(backgroundDirectory());
            String filename = "reader-background-" + user.getId() + "-" + UUID.randomUUID() + extension;
            storedFile = backgroundDirectory().resolve(filename);
            Files.write(storedFile, bytes);
            return backgroundRepository.save(ReaderBackground.builder()
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
                    HttpStatus.INTERNAL_SERVER_ERROR, "阅读背景图片保存失败", exception);
        }
    }

    private ReaderBackgroundDTO toDto(ReaderBackground background) {
        return new ReaderBackgroundDTO(
                background.getId(),
                background.getOriginalFilename(),
                coverDir + "/" + background.getStoredFilename(),
                background.getContentType(),
                background.getFileSize(),
                background.getCreatedAt());
    }

    private Path backgroundDirectory() {
        return Paths.get(uploadDir, coverDir).toAbsolutePath().normalize();
    }

    private Path backgroundPath(ReaderBackground background) {
        Path directory = backgroundDirectory();
        Path path = directory.resolve(background.getStoredFilename()).normalize();
        if (!path.startsWith(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "阅读背景文件路径无效");
        }
        return path;
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return "未命名阅读背景";
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
            // 数据库操作优先完成；残留文件可由存储清理任务处理。
        }
    }
}
