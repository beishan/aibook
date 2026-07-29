package com.aibook.service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.zip.InflaterInputStream;

/**
 * 字体文件头和常用 SFNT name 表读取器。
 */
@Component
public class FontFileInspector {

    private static final Set<String> EXTENSIONS =
            Set.of("ttf", "otf", "woff", "woff2");
    private static final int MAX_METADATA_TABLE_SIZE = 4 * 1024 * 1024;

    public boolean hasSupportedExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0
                && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public FontMetadata inspect(Path path) throws IOException {
        return inspect(path, path.getFileName().toString());
    }

    public FontMetadata inspect(Path path, String fallbackFilename) throws IOException {
        long size = Files.size(path);
        if (size < 12) {
            throw new IOException("字体文件内容不完整");
        }

        byte[] bytes = Files.readAllBytes(path);
        String format = detectFormat(bytes);
        if (format == null) {
            throw new IOException("文件头不是受支持的字体格式");
        }
        validateContainerHeader(bytes, format);
        String extension = extension(path);
        if (EXTENSIONS.contains(extension) && !format.equals(extension)) {
            throw new IOException("字体扩展名与文件内容不一致");
        }

        NameInfo names = switch (format) {
            case "ttf", "otf" -> readSfntNames(bytes);
            case "woff" -> readWoffNames(bytes);
            default -> new NameInfo(null, null);
        };
        String fallback = stripExtension(fallbackFilename);
        String family = cleanName(names.family());
        if (family == null) {
            family = fallback;
        }
        String subfamily = cleanName(names.subfamily());
        String searchableName = (family + " " + (subfamily == null ? "" : subfamily))
                .toLowerCase(Locale.ROOT);
        int weight = searchableName.contains("black") ? 900
                : searchableName.contains("extra bold")
                || searchableName.contains("extrabold") ? 800
                : searchableName.contains("bold") ? 700
                : searchableName.contains("semi bold")
                || searchableName.contains("semibold") ? 600
                : searchableName.contains("medium") ? 500
                : searchableName.contains("light") ? 300
                : 400;
        String style = searchableName.contains("italic")
                || searchableName.contains("oblique") ? "italic" : "normal";
        return new FontMetadata(format, family, weight, style);
    }

    private NameInfo readSfntNames(byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int tableCount = unsignedShort(buffer, 4);
        if (tableCount < 1 || tableCount > 256 || 12L + tableCount * 16L > bytes.length) {
            throw new IOException("字体表目录损坏");
        }
        for (int index = 0; index < tableCount; index++) {
            int position = 12 + index * 16;
            if ("name".equals(tag(bytes, position))) {
                long offset = unsignedInt(buffer, position + 8);
                long length = unsignedInt(buffer, position + 12);
                return parseNameTable(slice(bytes, offset, length));
            }
        }
        return new NameInfo(null, null);
    }

    private NameInfo readWoffNames(byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int tableCount = unsignedShort(buffer, 12);
        if (tableCount < 1 || tableCount > 256 || 44L + tableCount * 20L > bytes.length) {
            throw new IOException("WOFF 表目录损坏");
        }
        for (int index = 0; index < tableCount; index++) {
            int position = 44 + index * 20;
            if (!"name".equals(tag(bytes, position))) {
                continue;
            }
            long offset = unsignedInt(buffer, position + 4);
            long compressedLength = unsignedInt(buffer, position + 8);
            long originalLength = unsignedInt(buffer, position + 12);
            byte[] table = slice(bytes, offset, compressedLength);
            if (compressedLength < originalLength) {
                if (originalLength > MAX_METADATA_TABLE_SIZE) {
                    throw new IOException("WOFF 元数据表过大");
                }
                try (InflaterInputStream input = new InflaterInputStream(
                        new java.io.ByteArrayInputStream(table));
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    input.transferTo(output);
                    table = output.toByteArray();
                }
                if (table.length != originalLength) {
                    throw new IOException("WOFF 元数据解压长度不正确");
                }
            }
            return parseNameTable(table);
        }
        return new NameInfo(null, null);
    }

    private NameInfo parseNameTable(byte[] bytes) throws IOException {
        if (bytes.length < 6) {
            throw new IOException("字体名称表损坏");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int count = unsignedShort(buffer, 2);
        int stringOffset = unsignedShort(buffer, 4);
        if (count > 4096 || 6L + count * 12L > bytes.length) {
            throw new IOException("字体名称记录损坏");
        }

        NameCandidate family = null;
        NameCandidate subfamily = null;
        for (int index = 0; index < count; index++) {
            int position = 6 + index * 12;
            int platform = unsignedShort(buffer, position);
            int language = unsignedShort(buffer, position + 4);
            int nameId = unsignedShort(buffer, position + 6);
            int length = unsignedShort(buffer, position + 8);
            int offset = unsignedShort(buffer, position + 10);
            if (nameId != 1 && nameId != 2) {
                continue;
            }
            long start = (long) stringOffset + offset;
            if (start < 0 || start + length > bytes.length) {
                continue;
            }
            Charset charset = platform == 0 || platform == 3
                    ? StandardCharsets.UTF_16BE
                    : Charset.forName("x-MacRoman");
            String value = new String(bytes, (int) start, length, charset)
                    .replace("\u0000", "")
                    .trim();
            if (value.isEmpty()) {
                continue;
            }
            int score = platform == 3 ? 2 : platform == 0 ? 1 : 0;
            if (language == 0x0409 || language == 0x0804) {
                score += 2;
            }
            NameCandidate candidate = new NameCandidate(value, score);
            if (nameId == 1 && (family == null || score > family.score())) {
                family = candidate;
            } else if (nameId == 2
                    && (subfamily == null || score > subfamily.score())) {
                subfamily = candidate;
            }
        }
        return new NameInfo(
                family == null ? null : family.value(),
                subfamily == null ? null : subfamily.value());
    }

    private String detectFormat(byte[] bytes) {
        String signature = new String(bytes, 0, 4, StandardCharsets.ISO_8859_1);
        return switch (signature) {
            case "OTTO" -> "otf";
            case "wOFF" -> "woff";
            case "wOF2" -> "woff2";
            case "true", "\u0000\u0001\u0000\u0000" -> "ttf";
            default -> null;
        };
    }

    private void validateContainerHeader(byte[] bytes, String format) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if ("woff".equals(format)) {
            if (bytes.length < 44
                    || unsignedInt(buffer, 8) != bytes.length
                    || unsignedShort(buffer, 12) < 1) {
                throw new IOException("WOFF 文件头损坏");
            }
        } else if ("woff2".equals(format)) {
            if (bytes.length < 48
                    || unsignedInt(buffer, 8) != bytes.length
                    || unsignedShort(buffer, 12) < 1
                    || unsignedInt(buffer, 16) < 1) {
                throw new IOException("WOFF2 文件头损坏");
            }
        }
    }

    private byte[] slice(byte[] bytes, long offset, long length) throws IOException {
        if (length < 0 || length > MAX_METADATA_TABLE_SIZE
                || offset < 0 || offset + length > bytes.length) {
            throw new IOException("字体元数据表范围无效");
        }
        return java.util.Arrays.copyOfRange(
                bytes, Math.toIntExact(offset), Math.toIntExact(offset + length));
    }

    private int unsignedShort(ByteBuffer buffer, int position) {
        return Short.toUnsignedInt(buffer.getShort(position));
    }

    private long unsignedInt(ByteBuffer buffer, int position) {
        return Integer.toUnsignedLong(buffer.getInt(position));
    }

    private String tag(byte[] bytes, int position) {
        return new String(bytes, position, 4, StandardCharsets.ISO_8859_1);
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private String cleanName(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    public record FontMetadata(
            String format, String fontFamily, int fontWeight, String fontStyle) {
    }

    private record NameInfo(String family, String subfamily) {
    }

    private record NameCandidate(String value, int score) {
    }
}
