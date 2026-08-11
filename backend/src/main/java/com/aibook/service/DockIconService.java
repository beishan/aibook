package com.aibook.service;

import com.aibook.dto.DockIconStatusDTO;
import com.aibook.model.entity.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DockIconService {

    private static final long MAX_ICON_SIZE = 5L * 1024 * 1024;
    private static final List<String> ICON_NAMES =
            List.of(
                    "home", "library", "shelf", "repair", "settings",
                    "trashEmpty", "trashFull", "trash");
    private static final Set<String> ICON_NAME_SET = Set.copyOf(ICON_NAMES);
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    @Value("${upload.path:${app.upload.dir:/app/uploads}}")
    private String uploadPath;

    public DockIconStatusDTO getStatus(User user) {
        List<String> existing = ICON_NAMES.stream()
                .filter(name -> findIcon(user.getId(), name) != null)
                .toList();
        return new DockIconStatusDTO(existing);
    }

    public DockIconStatusDTO upload(User user, String name, MultipartFile file) {
        requireIconName(name);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择图标图片");
        }
        if (file.getSize() > MAX_ICON_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图标图片不能超过5MB");
        }

        Path temporaryFile = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            String extension = contentType == null ? null : SUPPORTED_TYPES.get(contentType);
            if (extension == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG 或 WebP 图片");
            }

            Path directory = iconDirectory(user.getId());
            Files.createDirectories(directory);
            temporaryFile = directory.resolve("." + UUID.randomUUID() + extension);
            Files.write(temporaryFile, bytes);
            deleteExistingIcons(user.getId(), name);
            Files.move(
                    temporaryFile,
                    directory.resolve(name + extension),
                    StandardCopyOption.REPLACE_EXISTING);
            temporaryFile = null;
            return getStatus(user);
        } catch (ResponseStatusException exception) {
            deleteQuietly(temporaryFile);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(temporaryFile);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "图标保存失败", exception);
        }
    }

    public DockIconContent getIcon(User user, String name) {
        requireIconName(name);
        Path icon = findIcon(user.getId(), name);
        if (icon == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未设置该图标");
        }
        return new DockIconContent(icon, contentType(icon.getFileName().toString()));
    }

    public DockIconStatusDTO delete(User user, String name) {
        requireIconName(name);
        deleteExistingIcons(user.getId(), name);
        return getStatus(user);
    }

    private void requireIconName(String name) {
        if (!ICON_NAME_SET.contains(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的 Dock 图标名称");
        }
    }

    private Path iconDirectory(Long userId) {
        return Paths.get(uploadPath, "dock-icons", String.valueOf(userId));
    }

    private Path findIcon(Long userId, String name) {
        Path directory = iconDirectory(userId);
        if (!Files.isDirectory(directory)) return null;
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(name + "."))
                    .filter(path -> isSupportedExtension(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "图标读取失败", exception);
        }
    }

    private void deleteExistingIcons(Long userId, String name) {
        Path directory = iconDirectory(userId);
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(name + "."))
                    .forEach(this::deleteQuietly);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "图标删除失败", exception);
        }
    }

    private boolean isSupportedExtension(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 保留原始异常或允许后续上传继续完成。
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

    private String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    public record DockIconContent(Path path, String contentType) {}
}
