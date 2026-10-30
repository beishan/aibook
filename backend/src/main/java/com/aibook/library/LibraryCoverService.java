package com.aibook.library;

import com.aibook.model.entity.User;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LibraryCoverService {
    private final LibraryService libraries;
    @Value("${upload.path:./uploads}")
    private String uploadPath;

    private Path path(User user, long id, String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图书馆封面不存在");
        }
        return Path.of(uploadPath).toAbsolutePath().normalize().resolve("library-covers")
                .resolve(String.valueOf(user.getId())).resolve(String.valueOf(id)).resolve(key + ".jpg");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config(Map<String, Object> library) {
        return new LinkedHashMap<>((Map<String, Object>) library.get("config"));
    }

    public Path image(User user, long id) {
        var library = libraries.owned(user, id, false);
        Path file = path(user, id, (String) config(library).get("coverImage"));
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图书馆封面不存在");
        return file;
    }

    @Transactional
    public Map<String, Object> upload(User user, long id, long revision, MultipartFile file) {
        var library = libraries.owned(user, id, false);
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("请选择不超过 5MB 的 PNG 或 JPEG 图片");
        }
        BufferedImage image;
        try (var source = file.getInputStream(); var input = ImageIO.createImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("请选择有效 PNG 或 JPEG 图片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"))
                        || width > 6000 || height > 6000 || (long) width * height > 16000000) {
                    throw new IllegalArgumentException("仅支持 PNG/JPEG，边长不超过 6000 像素且总像素不超过 1600 万");
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("图片无法读取，请选择有效 PNG 或 JPEG 图片", error);
        }
        String key = UUID.randomUUID().toString();
        Path target = path(user, id, key);
        try {
            Files.createDirectories(target.getParent());
            double scale = Math.min(1, 1600.0 / Math.max(image.getWidth(), image.getHeight()));
            var output = new BufferedImage(Math.max(1, (int) (image.getWidth() * scale)),
                    Math.max(1, (int) (image.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
            var graphics = output.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(image, 0, 0, output.getWidth(), output.getHeight(), null);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(output, "jpeg", target.toFile());
            var settings = config(library);
            String old = (String) settings.put("coverImage", key);
            var saved = libraries.save(user, id, revision, (String) library.get("name"),
                    (String) library.get("description"), settings);
            cleanupAfterTransaction(user, id, old, target);
            return saved;
        } catch (RuntimeException error) {
            delete(target);
            throw error;
        } catch (Exception error) {
            delete(target);
            throw new IllegalStateException("图书馆封面保存失败", error);
        }
    }

    @Transactional
    public Map<String, Object> remove(User user, long id, long revision) {
        var library = libraries.owned(user, id, false);
        var settings = config(library);
        String old = (String) settings.remove("coverImage");
        var result = libraries.save(user, id, revision, (String) library.get("name"),
                (String) library.get("description"), settings);
        cleanupAfterTransaction(user, id, old, null);
        return result;
    }

    private void cleanupAfterTransaction(User user, long id, String old, Path created) {
        Runnable cleanup = () -> {
            if (old != null) {
                try { delete(path(user, id, old)); } catch (ResponseStatusException ignored) { }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) cleanup.run();
                    else delete(created);
                }
            });
        } else cleanup.run();
    }

    public void cleanupDeletedLibrary(User user, long id) {
        Path directory = path(user, id, "00000000-0000-0000-0000-000000000000").getParent();
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.walk(directory)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(this::delete);
        } catch (Exception ignored) { }
    }

    private void delete(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (Exception ignored) { }
    }
}
