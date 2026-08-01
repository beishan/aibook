package com.aibook.service;

import com.aibook.dto.FontAssetDTO;
import com.aibook.dto.FontAssetUpdateRequest;
import com.aibook.dto.FontDirectoryNodeDTO;
import com.aibook.dto.FontScanDirectoryDTO;
import com.aibook.dto.FontScanResultDTO;
import com.aibook.exception.ResourceNotFoundException;
import com.aibook.model.entity.FontAsset;
import com.aibook.model.entity.FontScanDirectory;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.FontScanDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FontService {

    private static final long DEFAULT_MAX_FONT_SIZE = 50L * 1024 * 1024;

    private final FontAssetRepository fontAssetRepository;
    private final FontScanDirectoryRepository directoryRepository;
    private final FontFileInspector inspector;

    @Value("${upload.path:${app.upload.dir:/app/uploads}}")
    private String uploadRoot;

    @Value("${app.font.max-size:52428800}")
    private long maxFontSize = DEFAULT_MAX_FONT_SIZE;

    @Value("${app.font.scan-root:/fontfolder}")
    private String fontScanRoot;

    public List<FontAssetDTO> list() {
        return fontAssetRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<FontAssetDTO> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择字体文件");
        }
        List<FontAssetDTO> result = new ArrayList<>();
        for (MultipartFile file : files) {
            result.add(toDto(uploadOne(file)));
        }
        return result;
    }

    private FontAsset uploadOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字体文件不能为空");
        }
        if (file.getSize() > maxFontSize) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "单个字体文件不能超过50MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字体文件名不能为空");
        }
        Path originalPath;
        try {
            originalPath = Path.of(originalName).getFileName();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字体文件名无效");
        }
        if (!inspector.hasSupportedExtension(originalPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "仅支持 TTF、OTF、WOFF、WOFF2 字体");
        }

        Path directory = uploadDirectory();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".font-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = file.getInputStream();
                 DigestInputStream input = new DigestInputStream(raw, digest)) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(temporary) > maxFontSize) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "单个字体文件不能超过50MB");
            }
            FontFileInspector.FontMetadata metadata =
                    inspector.inspect(temporary, originalPath.toString());
            String displayName = preferredDisplayName(originalPath, metadata);
            String originalExtension = originalPath.toString()
                    .substring(originalPath.toString().lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            if (!metadata.format().equals(originalExtension)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "字体扩展名与文件内容不一致");
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            var duplicate = fontAssetRepository.findByFileHash(hash);
            if (duplicate.isPresent()) {
                FontAsset existing = duplicate.get();
                if (fileExistsAndAllowed(existing)) {
                    existing.setAvailable(true);
                    applyPreferredDisplayName(existing, displayName);
                    return fontAssetRepository.save(existing);
                }
                Path destination = directory.resolve(
                        UUID.randomUUID() + "." + metadata.format()).normalize();
                move(temporary, destination);
                temporary = null;
                applyMetadata(
                        existing, metadata, displayName, hash, Files.size(destination));
                existing.setSourceType(FontAsset.SourceType.UPLOADED);
                existing.setFilePath(destination.toString());
                existing.setScanDirectoryId(null);
                existing.setEnabled(true);
                existing.setAvailable(true);
                return fontAssetRepository.save(existing);
            }

            Path destination = directory.resolve(
                    UUID.randomUUID() + "." + metadata.format()).normalize();
            move(temporary, destination);
            temporary = null;
            FontAsset asset = FontAsset.builder()
                    .displayName(displayName)
                    .fontFamily(metadata.fontFamily())
                    .fontWeight(metadata.fontWeight())
                    .fontStyle(metadata.fontStyle())
                    .format(metadata.format())
                    .sourceType(FontAsset.SourceType.UPLOADED)
                    .filePath(destination.toString())
                    .fileHash(hash)
                    .fileSize(Files.size(destination))
                    .enabled(true)
                    .available(true)
                    .build();
            return fontAssetRepository.save(asset);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "字体文件无效: " + Objects.toString(exception.getMessage(), "读取失败"),
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    log.warn("无法清理字体临时文件: {}", temporary);
                }
            }
        }
    }

    @Transactional
    public FontAssetDTO update(Long id, FontAssetUpdateRequest request) {
        FontAsset asset = getAsset(id);
        if (request.getDisplayName() != null) {
            String value = request.getDisplayName().trim();
            if (value.isEmpty() || value.length() > 255) {
                throw new IllegalArgumentException("字体显示名称长度必须为1到255个字符");
            }
            asset.setDisplayName(value);
        }
        if (request.getEnabled() != null) {
            asset.setEnabled(request.getEnabled());
        }
        return toDto(fontAssetRepository.save(asset));
    }

    /** 浏览器确认无法解析字体后持久化不可用状态，避免后续重复加载。 */
    @Transactional
    public void markUnavailable(Long id) {
        FontAsset asset = getAsset(id);
        if (!Boolean.FALSE.equals(asset.getAvailable())) {
            asset.setAvailable(false);
            fontAssetRepository.save(asset);
        }
    }

    @Transactional
    public void delete(Long id) {
        FontAsset asset = getAsset(id);
        if (asset.getSourceType() == FontAsset.SourceType.UPLOADED) {
            Path path = resolveAllowedPath(asset);
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "上传字体文件删除失败", exception);
            }
        }
        fontAssetRepository.delete(asset);
    }

    public FontContent content(Long id) {
        FontAsset asset = getAsset(id);
        if (!Boolean.TRUE.equals(asset.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字体未启用");
        }
        Path path = resolveAllowedPath(asset);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            asset.setAvailable(false);
            fontAssetRepository.save(asset);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字体文件不可用");
        }
        String contentType = switch (asset.getFormat()) {
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
        return new FontContent(path, contentType, "\"" + asset.getFileHash() + "\"");
    }

    public List<FontScanDirectoryDTO> listDirectories() {
        return directoryRepository.findAllByOrderByPathAsc().stream()
                .map(this::toDto)
                .toList();
    }

    public List<FontDirectoryNodeDTO> browseDirectories(String value) {
        Path root = scanRoot();
        if (value == null || value.isBlank()) {
            return List.of(toDirectoryNode(root));
        }

        Path parent = validateBrowseDirectory(value, root);
        try (Stream<Path> paths = Files.list(parent)) {
            return paths
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(Files::isReadable)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .map(this::toDirectoryNode)
                    .toList();
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException("字体目录无法读取", exception);
        }
    }

    @Transactional
    public FontScanDirectoryDTO addDirectory(String value) {
        Path path = validateDirectory(value);
        String canonical = path.toString();
        if (directoryRepository.findByPath(canonical).isPresent()) {
            throw new IllegalArgumentException("该字体目录已添加");
        }
        FontScanDirectory directory = FontScanDirectory.builder()
                .path(canonical)
                .enabled(true)
                .build();
        return toDto(directoryRepository.save(directory));
    }

    @Transactional
    public void deleteDirectory(Long id) {
        FontScanDirectory directory = getDirectory(id);
        fontAssetRepository.deleteByScanDirectoryId(id);
        directoryRepository.delete(directory);
    }

    public FontScanResultDTO scan() {
        AtomicInteger scanned = new AtomicInteger();
        AtomicInteger added = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> errors = new ArrayList<>();
        for (FontScanDirectory directory
                : directoryRepository.findByEnabledTrueOrderByPathAsc()) {
            scanDirectory(
                    directory, scanned, added, updated, skipped, failed, errors);
        }
        return FontScanResultDTO.builder()
                .scanned(scanned.get())
                .newFonts(added.get())
                .updatedFonts(updated.get())
                .skippedFonts(skipped.get())
                .failedFonts(failed.get())
                .errors(List.copyOf(errors))
                .build();
    }

    private void scanDirectory(
            FontScanDirectory directory,
            AtomicInteger scanned,
            AtomicInteger added,
            AtomicInteger updated,
            AtomicInteger skipped,
            AtomicInteger failed,
            List<String> errors) {
        Path root;
        try {
            root = validateDirectory(directory.getPath());
        } catch (RuntimeException exception) {
            recordDirectoryError(directory, exception.getMessage());
            errors.add(directory.getPath() + ": " + exception.getMessage());
            failed.incrementAndGet();
            return;
        }

        Set<String> seen = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(inspector::hasSupportedExtension)
                    .forEach(path -> {
                        scanned.incrementAndGet();
                        try {
                            Path canonical = requireWithinRoot(path, root);
                            if (Files.size(canonical) > maxFontSize) {
                                throw new IOException("字体文件超过50MB");
                            }
                            seen.add(canonical.toString());
                            syncScannedFont(
                                    canonical, directory, added, updated, skipped);
                        } catch (Exception exception) {
                            failed.incrementAndGet();
                            errors.add(path + ": "
                                    + Objects.toString(exception.getMessage(), "扫描失败"));
                        }
                    });

            for (FontAsset asset
                    : fontAssetRepository.findByScanDirectoryId(directory.getId())) {
                boolean available = seen.contains(asset.getFilePath())
                        && Files.isRegularFile(Path.of(asset.getFilePath()));
                if (!Objects.equals(asset.getAvailable(), available)) {
                    asset.setAvailable(available);
                    fontAssetRepository.save(asset);
                }
            }
            directory.setLastError(null);
            directory.setLastScanAt(LocalDateTime.now());
            directoryRepository.save(directory);
        } catch (IOException | SecurityException exception) {
            recordDirectoryError(directory, exception.getMessage());
            errors.add(root + ": " + Objects.toString(exception.getMessage(), "目录读取失败"));
            failed.incrementAndGet();
        }
    }

    private void syncScannedFont(
            Path path,
            FontScanDirectory directory,
            AtomicInteger added,
            AtomicInteger updated,
            AtomicInteger skipped) throws IOException {
        FontFileInspector.FontMetadata metadata = inspector.inspect(path);
        String displayName = preferredDisplayName(path.getFileName(), metadata);
        String hash = sha256(path);
        var byPath = fontAssetRepository.findBySourceTypeAndFilePath(
                FontAsset.SourceType.SCANNED, path.toString());
        if (byPath.isPresent()) {
            FontAsset asset = byPath.get();
            if (hash.equals(asset.getFileHash())) {
                asset.setAvailable(true);
                applyPreferredDisplayName(asset, displayName);
                fontAssetRepository.save(asset);
                skipped.incrementAndGet();
                return;
            }
            var duplicate = fontAssetRepository.findByFileHash(hash);
            if (duplicate.isPresent() && !duplicate.get().getId().equals(asset.getId())) {
                fontAssetRepository.delete(asset);
                skipped.incrementAndGet();
                return;
            }
            applyMetadata(asset, metadata, displayName, hash, Files.size(path));
            asset.setAvailable(true);
            fontAssetRepository.save(asset);
            updated.incrementAndGet();
            return;
        }
        var duplicate = fontAssetRepository.findByFileHash(hash);
        if (duplicate.isPresent()) {
            FontAsset asset = duplicate.get();
            if (!fileExistsAndAllowed(asset)) {
                applyMetadata(asset, metadata, displayName, hash, Files.size(path));
                asset.setSourceType(FontAsset.SourceType.SCANNED);
                asset.setFilePath(path.toString());
                asset.setScanDirectoryId(directory.getId());
                asset.setAvailable(true);
                fontAssetRepository.save(asset);
                updated.incrementAndGet();
                return;
            }
            skipped.incrementAndGet();
            return;
        }
        FontAsset asset = FontAsset.builder()
                .displayName(displayName)
                .fontFamily(metadata.fontFamily())
                .fontWeight(metadata.fontWeight())
                .fontStyle(metadata.fontStyle())
                .format(metadata.format())
                .sourceType(FontAsset.SourceType.SCANNED)
                .filePath(path.toString())
                .fileHash(hash)
                .fileSize(Files.size(path))
                .scanDirectoryId(directory.getId())
                .enabled(true)
                .available(true)
                .build();
        fontAssetRepository.save(asset);
        added.incrementAndGet();
    }

    private void applyMetadata(
            FontAsset asset,
            FontFileInspector.FontMetadata metadata,
            String displayName,
            String hash,
            long size) {
        asset.setDisplayName(displayName);
        asset.setFontFamily(metadata.fontFamily());
        asset.setFontWeight(metadata.fontWeight());
        asset.setFontStyle(metadata.fontStyle());
        asset.setFormat(metadata.format());
        asset.setFileHash(hash);
        asset.setFileSize(size);
    }

    private String preferredDisplayName(
            Path filename,
            FontFileInspector.FontMetadata metadata) {
        String value = filename == null || filename.getFileName() == null
                ? ""
                : filename.getFileName().toString().trim();
        int dot = value.lastIndexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot).trim();
        }
        return value.isEmpty() ? metadata.fontFamily() : value;
    }

    private void applyPreferredDisplayName(FontAsset asset, String displayName) {
        if (asset.getDisplayName() == null
                || asset.getDisplayName().isBlank()
                || Objects.equals(asset.getDisplayName(), asset.getFontFamily())) {
            asset.setDisplayName(displayName);
        }
    }

    private Path validateDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("字体目录路径不能为空");
        }
        Path path;
        try {
            path = Path.of(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("字体目录路径无效", exception);
        }
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("字体目录必须使用容器内绝对路径");
        }
        try {
            Path real = path.toRealPath();
            if (!Files.isDirectory(real) || !Files.isReadable(real)) {
                throw new IllegalArgumentException("字体目录不存在或没有读取权限");
            }
            Path allowedRoot = scanRoot();
            if (!real.startsWith(allowedRoot)) {
                throw new IllegalArgumentException(
                        "字体目录必须位于扫描根目录 " + allowedRoot + " 内");
            }
            return real;
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException("字体目录不存在或没有读取权限", exception);
        }
    }

    private Path validateBrowseDirectory(String value, Path root) {
        Path path;
        try {
            path = Path.of(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("字体目录路径无效", exception);
        }
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("字体目录必须使用容器内绝对路径");
        }
        try {
            Path real = path.toRealPath();
            if (!real.startsWith(root)
                    || !Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(real)) {
                throw new IllegalArgumentException("只能浏览字体扫描根目录内的目录");
            }
            return real;
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException("字体目录不存在或没有读取权限", exception);
        }
    }

    private FontDirectoryNodeDTO toDirectoryNode(Path path) {
        return FontDirectoryNodeDTO.builder()
                .name(path.getFileName() == null
                        ? path.toString()
                        : path.getFileName().toString())
                .path(path.toString())
                .leaf(!hasDirectoryChildren(path))
                .build();
    }

    private boolean hasDirectoryChildren(Path path) {
        try (Stream<Path> children = Files.list(path)) {
            return children.anyMatch(child ->
                    !child.getFileName().toString().startsWith(".")
                            && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                            && Files.isReadable(child));
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private Path scanRoot() {
        Path configured;
        try {
            configured = Path.of(fontScanRoot).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("字体扫描根目录配置无效", exception);
        }
        try {
            Path real = configured.toRealPath();
            if (!Files.isDirectory(real) || !Files.isReadable(real)) {
                throw new IllegalArgumentException(
                        "字体扫描根目录不存在或没有读取权限: " + configured);
            }
            return real;
        } catch (IOException | SecurityException exception) {
            throw new IllegalArgumentException(
                    "字体扫描根目录不存在或没有读取权限: " + configured, exception);
        }
    }

    private Path requireWithinRoot(Path path, Path root) throws IOException {
        Path canonicalRoot = root.toRealPath();
        Path canonical = path.toRealPath();
        if (!canonical.startsWith(canonicalRoot)) {
            throw new IOException("字体文件超出扫描目录");
        }
        return canonical;
    }

    private boolean fileExistsAndAllowed(FontAsset asset) {
        try {
            Path path = resolveAllowedPath(asset);
            return Files.isRegularFile(path) && Files.isReadable(path);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Path resolveAllowedPath(FontAsset asset) {
        try {
            Path candidate = Path.of(asset.getFilePath()).toAbsolutePath().normalize();
            Path root;
            if (asset.getSourceType() == FontAsset.SourceType.UPLOADED) {
                root = uploadDirectory().toAbsolutePath().normalize();
            } else {
                FontScanDirectory directory = directoryRepository
                        .findById(asset.getScanDirectoryId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "字体扫描目录不存在"));
                root = validateDirectory(directory.getPath());
            }
            if (!candidate.startsWith(root)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "字体文件路径无效");
            }
            if (Files.exists(candidate)) {
                Path realRoot = root.toRealPath();
                Path realCandidate = candidate.toRealPath();
                if (!realCandidate.startsWith(realRoot)) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "字体文件路径无效");
                }
                return realCandidate;
            }
            return candidate;
        } catch (IOException | SecurityException exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "字体文件路径无法访问", exception);
        }
    }

    private Path uploadDirectory() {
        return Path.of(uploadRoot).toAbsolutePath().normalize().resolve("fonts");
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = Files.newInputStream(path);
                 DigestInputStream input = new DigestInputStream(raw, digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void recordDirectoryError(FontScanDirectory directory, String message) {
        directory.setLastScanAt(LocalDateTime.now());
        directory.setLastError(Objects.toString(message, "目录读取失败"));
        directoryRepository.save(directory);
    }

    private FontAsset getAsset(Long id) {
        return fontAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("字体", id));
    }

    private FontScanDirectory getDirectory(Long id) {
        return directoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("字体扫描目录", id));
    }

    private FontAssetDTO toDto(FontAsset asset) {
        boolean available = Boolean.TRUE.equals(asset.getAvailable())
                && fileExistsAndAllowed(asset);
        return FontAssetDTO.builder()
                .id(asset.getId())
                .displayName(asset.getDisplayName())
                .fontFamily(asset.getFontFamily())
                .fontWeight(asset.getFontWeight())
                .fontStyle(asset.getFontStyle())
                .format(asset.getFormat())
                .sourceType(asset.getSourceType())
                .filePath(asset.getFilePath())
                .fileSize(asset.getFileSize())
                .enabled(asset.getEnabled())
                .available(available)
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }

    private FontScanDirectoryDTO toDto(FontScanDirectory directory) {
        return FontScanDirectoryDTO.builder()
                .id(directory.getId())
                .path(directory.getPath())
                .enabled(directory.getEnabled())
                .lastScanAt(directory.getLastScanAt())
                .lastError(directory.getLastError())
                .createdAt(directory.getCreatedAt())
                .updatedAt(directory.getUpdatedAt())
                .build();
    }

    public record FontContent(Path path, String contentType, String etag) {
    }
}
