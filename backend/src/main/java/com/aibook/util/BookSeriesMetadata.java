package com.aibook.util;

import com.aibook.model.entity.Book;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class BookSeriesMetadata {
    private BookSeriesMetadata() { }

    public static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (normalized.length() > 120) throw invalid("系列名称不能超过120个字符");
        return normalized;
    }

    /** 传入系列名称即替换名称与卷序；空名称表示移出系列。 */
    public static void apply(Book book, String name, BigDecimal index) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            book.setSeriesName(null);
            book.setSeriesIndex(null);
            return;
        }
        if (index != null && (index.signum() < 0 || index.compareTo(new BigDecimal("9999.99")) > 0
                || index.stripTrailingZeros().scale() > 2)) {
            throw invalid("卷序需为0至9999.99之间的数字，最多两位小数");
        }
        book.setSeriesName(normalized);
        book.setSeriesIndex(index);
    }

    public static BigDecimal parseIndex(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.strip()); }
        catch (NumberFormatException exception) { throw invalid("卷序必须是数字"); }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
