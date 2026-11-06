package com.aibook.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Reads bounded CBZ pages without extracting the archive to disk. */
@Service
public class ComicArchiveService {

    private static final long MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_DIRECTORY_BYTES = 8 * 1024 * 1024;
    private static final int END_RECORD_BYTES = 22;
    private static final int MAX_ZIP_COMMENT_BYTES = 65_535;
    private static final int MAX_PAGE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int MAX_DIMENSION = 32_768;

    private final Semaphore readers = new Semaphore(4);
    private final Cache<Source, Archive> archives = Caffeine.newBuilder()
            .maximumWeight(8_000_000)
            .weigher((Source source, Archive archive) -> archive.weight())
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    public record Source(Path path, long size, String stamp) {
        public String revision() {
            try {
                byte[] identity = (path + "\n" + size + "\n" + stamp)
                        .getBytes(StandardCharsets.UTF_8);
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    record Entry(String name, int ordinal) {}

    public record Archive(Source source, List<Entry> entries) {
        public int pageCount() {
            return entries.size();
        }

        int weight() {
            return 256 + entries.stream().mapToInt(entry -> 64 + 2 * entry.name().length()).sum();
        }
    }

    public record Page(byte[] bytes, String contentType, int width, int height) {}

    public Source source(String filePath, String fileHash) {
        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile() || !Files.isReadable(path)) {
                throw unavailable();
            }
            if (attributes.size() > MAX_ARCHIVE_BYTES) {
                throw limit("漫画归档不能超过 2 GiB");
            }
            return new Source(path, attributes.size(),
                    attributes.lastModifiedTime() + ":" + attributes.creationTime() + ":"
                            + attributes.fileKey() + ":" + fileHash);
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    public Archive archive(Source source) {
        return archives.get(source, this::index);
    }

    private Archive index(Source source) {
        acquireReader();
        try (ZipFile zip = openZip(source)) {
            if (zip.size() > MAX_ENTRIES) {
                throw limit("漫画归档条目不能超过 10000 个");
            }
            List<Entry> entries = new ArrayList<>();
            var items = zip.entries();
            int ordinal = 0;
            while (items.hasMoreElements()) {
                ZipEntry item = items.nextElement();
                if (!item.isDirectory() && isPageName(item.getName())) {
                    entries.add(new Entry(item.getName(), ordinal));
                }
                ordinal++;
            }
            if (entries.isEmpty()) {
                throw unreadable("漫画归档没有受支持的图片");
            }
            entries.sort(Comparator.comparing(Entry::name, ComicArchiveService::comparePaths)
                    .thenComparingInt(Entry::ordinal));
            return new Archive(source, List.copyOf(entries));
        } catch (ZipException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().toLowerCase(Locale.ROOT).contains("encrypted")) {
                throw unreadable("暂不支持加密的漫画归档");
            }
            throw unreadable("漫画归档已损坏或内容不是 ZIP");
        } catch (IOException exception) {
            throw unavailable();
        } finally {
            readers.release();
        }
    }

    private ZipFile openZip(Source source) throws IOException {
        // ZipFile loads the central directory eagerly. Bound it before opening an untrusted archive.
        int tailLength = (int) Math.min(source.size(), END_RECORD_BYTES + MAX_ZIP_COMMENT_BYTES + 20);
        byte[] tail = new byte[tailLength];
        try (var file = Files.newByteChannel(source.path())) {
            file.position(source.size() - tailLength);
            ByteBuffer buffer = ByteBuffer.wrap(tail);
            while (buffer.hasRemaining()) {
                if (file.read(buffer) < 0) {
                    throw new ZipException("Truncated ZIP");
                }
            }
        }
        for (int index = tail.length - END_RECORD_BYTES; index >= 0; index--) {
            if (little(tail, index, 4) != 0x06054b50
                    || index + END_RECORD_BYTES + little(tail, index + 20, 2) != tail.length) {
                continue;
            }
            if (little(tail, index + 4, 4) != 0
                    || little(tail, index + 8, 2) != little(tail, index + 10, 2)) {
                throw unreadable("暂不支持分卷漫画归档");
            }
            int entries = little(tail, index + 10, 2);
            long directory = Integer.toUnsignedLong(little(tail, index + 12, 4));
            if (entries > MAX_ENTRIES || directory > MAX_DIRECTORY_BYTES) {
                throw limit("漫画归档目录不能超过 10000 个条目或 8 MiB");
            }
            // A ZIP64 locator can replace the directory bounds. This feature accepts standard
            // ZIP directories within the limits above, so do not allow it to override them.
            if (index >= 20 && little(tail, index - 20, 4) == 0x07064b50) {
                throw unreadable("暂不支持使用 ZIP64 目录的漫画归档");
            }
            long directoryOffset = Integer.toUnsignedLong(little(tail, index + 16, 4));
            if (directoryOffset + directory != source.size() - tail.length + index) {
                throw new ZipException("Invalid ZIP directory offset");
            }
            return new ZipFile(source.path().toFile());
        }
        throw new ZipException("Missing ZIP end record");
    }

    public Optional<Page> cover(Source source) {
        Archive archive = archive(source);
        for (int number = 1; number <= Math.min(archive.pageCount(), 32); number++) {
            try {
                Page candidate = page(archive, number);
                if (candidate.bytes().length <= 10 * 1024 * 1024 && canReadCover(candidate)) {
                    return Optional.of(candidate);
                }
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.UNPROCESSABLE_ENTITY
                        && exception.getStatusCode() != HttpStatus.PAYLOAD_TOO_LARGE) {
                    throw exception;
                }
            }
        }
        return Optional.empty();
    }

    private boolean canReadCover(Page candidate) {
        if ("image/webp".equals(candidate.contentType())) {
            // The browser decodes WebP; Java's bundled ImageIO readers handle JPEG and PNG.
            return true;
        }
        acquireReader();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(candidate.bytes()))) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input, true, true);
                var parameters = reader.getDefaultReadParam();
                int stride = Math.max(1, Math.max(candidate.width(), candidate.height()) / 512);
                parameters.setSourceSubsampling(stride, stride, 0, 0);
                reader.read(0, parameters).flush();
                return true;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            return false;
        } finally {
            readers.release();
        }
    }

    public Page page(Archive archive, int page) {
        if (page < 1 || page > archive.pageCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码超出范围");
        }
        Entry entry = archive.entries().get(page - 1);
        acquireReader();
        try (ZipFile zip = openZip(archive.source())) {
            // Resolve the central-directory ordinal, including ZIPs with repeated entry names.
            var entries = zip.entries();
            ZipEntry item = null;
            for (int index = 0; index <= entry.ordinal() && entries.hasMoreElements(); index++) {
                item = entries.nextElement();
            }
            if (item == null || !item.getName().equals(entry.name())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "漫画文件已更新，请重新加载");
            }
            if (item.getSize() > MAX_PAGE_BYTES) {
                throw limit("单张漫画图片不能超过 16 MiB");
            }
            try (InputStream stream = zip.getInputStream(item)) {
                byte[] bytes = stream.readNBytes(MAX_PAGE_BYTES + 1);
                if (bytes.length > MAX_PAGE_BYTES) {
                    throw limit("单张漫画图片不能超过 16 MiB");
                }
                CRC32 crc = new CRC32();
                crc.update(bytes);
                if (crc.getValue() != item.getCrc() || bytes.length != item.getSize()) {
                    throw unreadable("漫画图片已损坏");
                }
                return inspectImage(bytes);
            }
        } catch (java.nio.file.NoSuchFileException exception) {
            throw unavailable();
        } catch (IOException | IndexOutOfBoundsException exception) {
            throw unreadable("漫画图片已损坏或暂不可读取");
        } finally {
            readers.release();
        }
    }

    private static boolean isPageName(String name) {
        String[] segments = name.replace('\\', '/').split("/");
        if (segments.length == 0 || Arrays.stream(segments)
                .anyMatch(segment -> segment.startsWith(".") || segment.equalsIgnoreCase("__MACOSX"))) {
            return false;
        }
        String lower = segments[segments.length - 1].toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    static int comparePaths(String left, String right) {
        String[] a = left.replace('\\', '/').split("/");
        String[] b = right.replace('\\', '/').split("/");
        for (int index = 0; index < Math.min(a.length, b.length); index++) {
            int difference = compareNatural(a[index].toLowerCase(Locale.ROOT),
                    b[index].toLowerCase(Locale.ROOT));
            if (difference != 0) {
                return difference;
            }
        }
        int depth = Integer.compare(a.length, b.length);
        return depth == 0 ? left.compareTo(right) : depth;
    }

    private static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (isDigit(x) && isDigit(y)) {
                int endA = digitEnd(a, i);
                int endB = digitEnd(b, j);
                while (i < endA - 1 && a.charAt(i) == '0') {
                    i++;
                }
                while (j < endB - 1 && b.charAt(j) == '0') {
                    j++;
                }
                int size = Integer.compare(endA - i, endB - j);
                if (size != 0) {
                    return size;
                }
                int digits = a.substring(i, endA).compareTo(b.substring(j, endB));
                if (digits != 0) {
                    return digits;
                }
                i = endA;
                j = endB;
            } else {
                if (x != y) {
                    return Character.compare(x, y);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static int digitEnd(String value, int index) {
        while (index < value.length() && isDigit(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static Page inspectImage(byte[] bytes) throws IOException {
        if (bytes.length >= 30 && tag(bytes, 0, "RIFF") && tag(bytes, 8, "WEBP")) {
            return inspectWebp(bytes);
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadable("漫画图片已损坏或格式不受支持");
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!"jpeg".equals(format) && !"png".equals(format)) {
                    throw unreadable("漫画图片格式不受支持");
                }
                reader.setInput(input, true, true);
                return checkedPage(bytes, "image/" + format, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private static Page inspectWebp(byte[] bytes) {
        if (little(bytes, 4, 4) + 8L != bytes.length) {
            throw unreadable("WebP 漫画图片已损坏");
        }
        if (tag(bytes, 12, "VP8X")) {
            if ((bytes[20] & 2) != 0) {
                throw unreadable("暂不支持动态 WebP 漫画图片");
            }
            return checkedPage(bytes, "image/webp",
                    1 + little(bytes, 24, 3), 1 + little(bytes, 27, 3));
        }
        if (tag(bytes, 12, "VP8L") && bytes[20] == 0x2f) {
            int dimensions = little(bytes, 21, 4);
            return checkedPage(bytes, "image/webp",
                    (dimensions & 0x3fff) + 1, ((dimensions >>> 14) & 0x3fff) + 1);
        }
        if (tag(bytes, 12, "VP8 ") && little(bytes, 23, 3) == 0x2a019d) {
            return checkedPage(bytes, "image/webp",
                    little(bytes, 26, 2) & 0x3fff, little(bytes, 28, 2) & 0x3fff);
        }
        throw unreadable("WebP 漫画图片已损坏");
    }

    private static boolean tag(byte[] bytes, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int little(byte[] bytes, int offset, int length) {
        int result = 0;
        for (int index = 0; index < length; index++) {
            result |= (bytes[offset + index] & 0xff) << (index * 8);
        }
        return result;
    }

    private static Page checkedPage(byte[] bytes, String type, int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_PIXELS) {
            throw limit("漫画图片的尺寸超出阅读限制");
        }
        return new Page(bytes, type, width, height);
    }

    private void acquireReader() {
        if (!readers.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "漫画加载繁忙，请重试");
        }
    }

    private static ResponseStatusException limit(String message) {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    private static ResponseStatusException unreadable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "漫画文件暂不可用");
    }
}
