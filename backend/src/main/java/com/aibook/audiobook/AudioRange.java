package com.aibook.audiobook;

/** Single byte range. Rejects malformed, multiple and unsatisfiable ranges. */
public record AudioRange(long start, long end, boolean partial) {
    public long length() { return end - start + 1; }

    public static AudioRange parse(String header, long size) {
        if (size <= 0) throw new IllegalArgumentException("音频为空");
        if (header == null) return new AudioRange(0, size - 1, false);
        if (header.length() > 100 || !header.matches("bytes=[0-9]*-[0-9]*")) throw new IllegalArgumentException("Range 无效");
        var parts = header.substring(6).split("-", -1);
        try {
            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]);
                if (suffix <= 0) throw new IllegalArgumentException("Range 无效");
                return new AudioRange(Math.max(0, size - suffix), size - 1, true);
            }
            long start = Long.parseLong(parts[0]);
            long end = parts[1].isEmpty() ? size - 1 : Math.min(Long.parseLong(parts[1]), size - 1);
            if (start >= size || start > end) throw new IllegalArgumentException("Range 超出文件");
            return new AudioRange(start, end, true);
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("Range 无效", exception); }
    }
}
