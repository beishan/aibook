package com.aibook.service;

import com.aibook.dto.UserProfileDTO;
import com.aibook.dto.UserProfileUpdateRequest;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final UserRepository userRepository;
    private final OperationLogService operationLogService;

    @Value("${upload.path:${app.upload.dir:/app/uploads}}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(User user) {
        return toDTO(user);
    }

    @Transactional
    public UserProfileDTO updateProfile(User user, UserProfileUpdateRequest request) {
        user.setNickname(normalize(request.getNickname()));
        user.setMood(normalize(request.getMood()));
        user.setProfileNotes(normalize(request.getNotes()));
        user.setBirthDate(request.getBirthDate());
        user.setBookPreferences(normalize(request.getBookPreferences()));
        User saved = userRepository.save(user);
        operationLogService.record(
                saved,
                OperationLog.Action.UPDATE_PROFILE,
                null,
                "更新个人资料",
                null);
        return toDTO(saved);
    }

    @Transactional
    public UserProfileDTO uploadAvatar(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像图片不能超过5MB");
        }

        Path newFile = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            String extension = contentType == null ? null : SUPPORTED_TYPES.get(contentType);
            if (extension == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP 或 GIF 图片");
            }

            Path directory = avatarDirectory(user.getId());
            Files.createDirectories(directory);
            newFile = directory.resolve(UUID.randomUUID() + extension);
            Files.write(newFile, bytes);

            String oldAvatar = user.getAvatarUrl();
            user.setAvatarUrl(relativeAvatarPath(user.getId(), newFile.getFileName().toString()));
            User saved = userRepository.save(user);
            operationLogService.record(
                    saved,
                    OperationLog.Action.UPDATE_AVATAR,
                    null,
                    "更新个人头像",
                    null);
            deleteManagedAvatar(user.getId(), oldAvatar);
            return toDTO(saved);
        } catch (ResponseStatusException exception) {
            deleteQuietly(newFile);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(newFile);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败", exception);
        }
    }

    @Transactional
    public UserProfileDTO deleteAvatar(User user) {
        String oldAvatar = user.getAvatarUrl();
        user.setAvatarUrl(null);
        User saved = userRepository.save(user);
        deleteManagedAvatar(user.getId(), oldAvatar);
        operationLogService.record(
                saved,
                OperationLog.Action.UPDATE_AVATAR,
                null,
                "移除个人头像",
                null);
        return toDTO(saved);
    }

    public void deleteStoredAvatar(User user) {
        deleteManagedAvatar(user.getId(), user.getAvatarUrl());
    }

    @Transactional(readOnly = true)
    public AvatarContent getAvatar(User user) {
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未设置头像");
        }
        Path directory = avatarDirectory(user.getId()).toAbsolutePath().normalize();
        Path avatar = Paths.get(uploadPath).resolve(avatarUrl).toAbsolutePath().normalize();
        if (!avatar.startsWith(directory) || !Files.isRegularFile(avatar)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在");
        }
        return new AvatarContent(avatar, contentType(avatar.getFileName().toString()));
    }

    private UserProfileDTO toDTO(User user) {
        boolean hasAvatar = user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(hasAvatar ? "/api/user/profile/avatar" : null)
                .hasAvatar(hasAvatar)
                .avatarVersion(hasAvatar ? avatarVersion(user) : null)
                .mood(user.getMood())
                .notes(user.getProfileNotes())
                .birthDate(user.getBirthDate())
                .bookPreferences(user.getBookPreferences())
                .role(user.getRole().name())
                .build();
    }

    private String avatarVersion(User user) {
        try {
            Path avatar = Paths.get(uploadPath).resolve(user.getAvatarUrl());
            if (!Files.isRegularFile(avatar)) return null;
            return Files.getLastModifiedTime(avatar).toMillis()
                    + "-" + avatar.getFileName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private Path avatarDirectory(Long userId) {
        return Paths.get(uploadPath, "avatars", String.valueOf(userId));
    }

    private String relativeAvatarPath(Long userId, String filename) {
        return Paths.get("avatars", String.valueOf(userId), filename).toString();
    }

    private void deleteManagedAvatar(Long userId, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return;
        try {
            Path directory = avatarDirectory(userId).toAbsolutePath().normalize();
            Path avatar = Paths.get(uploadPath).resolve(avatarUrl).toAbsolutePath().normalize();
            if (avatar.startsWith(directory)) {
                Files.deleteIfExists(avatar);
            }
        } catch (Exception ignored) {
            // 头像记录已经更新，旧文件清理失败不影响用户继续使用。
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 保留原始异常。
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

    private String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    public record AvatarContent(Path path, String contentType) {}
}
