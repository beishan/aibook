package com.aibook.util;

import com.aibook.dto.crawler.CrawlerAutoImportDtos.*;
import com.aibook.model.entity.CrawlerBook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Fixed predicates only: rules are OR-ed, expressions within a rule are AND-ed. */
public final class CrawlerAutoImportRules {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_JSON_LENGTH = 131072;

    private CrawlerAutoImportRules() { }

    public static Configuration defaults() {
        return new Configuration(false, List.of("STRUCTURED"), List.of());
    }

    public static Configuration normalize(Configuration configuration) {
        require(configuration != null, "缺少自动入库配置");
        List<String> formats = CrawlerImportFormats.normalize(configuration.formats());
        require(configuration.rules() != null && configuration.rules().size() <= 50,
                "最多配置 50 条自动入库规则");
        require(!configuration.enabled() || !configuration.rules().isEmpty(),
                "开启自动入库时请至少配置一条规则");
        List<Rule> rules = new ArrayList<>();
        for (Rule rule : configuration.rules()) {
            require(rule != null && rule.name() != null && !rule.name().isBlank()
                    && rule.name().trim().length() <= 100, "规则名称不能为空且不能超过 100 字");
            require(rule.expressions() != null && !rule.expressions().isEmpty()
                    && rule.expressions().size() <= 20, "每条规则需要 1 至 20 个子表达式");
            List<Expression> expressions = new ArrayList<>();
            for (Expression expression : rule.expressions()) {
                require(expression != null && expression.type() != null, "请选择子表达式类型");
                if (expression.type() == ExpressionType.CHAPTER_COUNT) {
                    require(expression.comparison() != null && expression.chapterCount() != null
                            && expression.chapterCount() >= 0, "章节数条件需要比较方式和非负整数");
                    expressions.add(new Expression(expression.type(), List.of(),
                            expression.comparison(), expression.chapterCount()));
                } else {
                    require(expression.values() != null && !expression.values().isEmpty()
                            && expression.values().size() <= 50, "关键词或标签需要填写 1 至 50 项");
                    Set<String> values = new LinkedHashSet<>();
                    for (String value : expression.values()) {
                        require(value != null && !value.isBlank() && value.trim().length() <= 200,
                                "关键词或标签不能为空且不能超过 200 字");
                        values.add(value.trim());
                    }
                    expressions.add(new Expression(expression.type(), List.copyOf(values), null, null));
                }
            }
            rules.add(new Rule(rule.name().trim(), List.copyOf(expressions)));
        }
        Configuration result = new Configuration(configuration.enabled(), formats, List.copyOf(rules));
        require(encode(result).length() <= MAX_JSON_LENGTH, "自动入库配置过大，请减少规则或关键词");
        return result;
    }

    public static String encode(Configuration configuration) {
        try {
            return MAPPER.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存自动入库配置失败", exception);
        }
    }

    public static Configuration decode(String json) {
        if (json == null || json.isBlank()) return defaults();
        try {
            require(json.length() <= MAX_JSON_LENGTH, "自动入库配置过大");
            return normalize(MAPPER.readValue(json, Configuration.class));
        } catch (Exception exception) {
            throw new IllegalStateException("网站自动入库配置损坏，请重新保存配置", exception);
        }
    }

    public static boolean matches(Configuration configuration, CrawlerBook book) {
        if (!configuration.enabled() || book == null
                || (book.getDiscoveryStatus() != null
                    && book.getDiscoveryStatus() != CrawlerBook.DiscoveryStatus.ACTIVE)) {
            return false;
        }
        return configuration.rules().stream().anyMatch(rule -> !rule.expressions().isEmpty()
                && rule.expressions().stream().allMatch(expression -> matches(expression, book)));
    }

    private static boolean matches(Expression expression, CrawlerBook book) {
        return switch (expression.type()) {
            case KEYWORD -> expression.values().stream().anyMatch(keyword ->
                    contains(book.getBookName(), keyword) || contains(book.getAuthor(), keyword)
                            || contains(book.getDescription(), keyword));
            case TAG -> {
                Set<String> tags = new HashSet<>();
                if (book.getTags() != null) {
                    Arrays.stream(book.getTags().split("[\\r\\n,，、]+"))
                            .map(String::trim).filter(tag -> !tag.isEmpty()).forEach(tags::add);
                }
                yield expression.values().stream().anyMatch(tags::contains);
            }
            case CHAPTER_COUNT -> book.getChapterCount() != null && switch (expression.comparison()) {
                case GT -> book.getChapterCount() > expression.chapterCount();
                case GTE -> book.getChapterCount() >= expression.chapterCount();
                case EQ -> book.getChapterCount().equals(expression.chapterCount());
                case NE -> !book.getChapterCount().equals(expression.chapterCount());
                case LT -> book.getChapterCount() < expression.chapterCount();
                case LTE -> book.getChapterCount() <= expression.chapterCount();
            };
        };
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
