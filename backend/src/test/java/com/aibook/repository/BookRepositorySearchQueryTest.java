package com.aibook.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 书籍检索查询静态守卫：确保全文检索原生查询与降级 JPQL 查询的
 * 参数声明、排序权重与书库可见性条件保持完整。
 */
class BookRepositorySearchQueryTest {

    private static final Pattern NAMED_PARAMETER = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");

    @Test
    void fullTextCountQueryDeclaresFilterParametersWithoutOrderOnlyOnes() throws NoSuchMethodException {
        Method method = BookRepository.class.getMethod(
                "searchFullText",
                Long.class, String.class, String.class, String.class, String.class,
                boolean.class, org.springframework.data.domain.Pageable.class);
        Query query = method.getAnnotation(Query.class);

        Set<String> expected = Set.of("userId", "keyword", "infix", "pinyinInfix", "isAscii");
        assertEquals(expected, namedParameters(query.countQuery()),
                "count 查询必须声明全部过滤参数；prefix 仅用于主查询排序，不应出现");
        assertFalse(query.countQuery().toLowerCase().contains("order by"),
                "count 查询不应包含排序");
    }

    @Test
    void fullTextQueryRanksExactThenPrefixThenContainsThenPinyin() throws NoSuchMethodException {
        Method method = BookRepository.class.getMethod(
                "searchFullText",
                Long.class, String.class, String.class, String.class, String.class,
                boolean.class, org.springframework.data.domain.Pageable.class);
        String value = method.getAnnotation(Query.class).value();

        assertTrue(value.contains("search_vector @@ plainto_tsquery('chinese_zh', :keyword)"),
                "必须包含 chinese_zh 全文匹配");
        int exact = value.indexOf("lower(b.title) = lower(:keyword) THEN 100");
        int prefix = value.indexOf("lower(b.title) LIKE :prefix THEN 60");
        int contains = value.indexOf("lower(b.title) LIKE :infix THEN 40");
        int author = value.indexOf("LIKE :infix THEN 30");
        int pinyin = value.indexOf("LIKE :pinyinInfix THEN 20");
        int rank = value.indexOf("ts_rank_cd(");
        assertTrue(exact >= 0 && prefix > exact && contains > prefix && author > contains
                        && pinyin > author && rank > 0,
                "排序权重必须满足：书名精确 > 书名前缀 > 书名包含 > 作者包含 > 拼音 > 相关度");
        assertTrue(value.contains("b.deleted_at IS NULL"), "必须过滤回收站书籍");
        assertTrue(value.contains("book_scan_sources"), "必须应用书库可见性条件");
    }

    @Test
    void fallbackQueryMatchesOriginalColumnsPlusPinyin() throws NoSuchMethodException {
        Method method = BookRepository.class.getMethod(
                "searchByKeyword",
                com.aibook.model.entity.User.class, String.class, String.class,
                boolean.class, org.springframework.data.domain.Pageable.class);
        String value = method.getAnnotation(Query.class).value();

        for (String column : new String[] {"b.title", "b.author", "b.isbn", "b.description"}) {
            assertTrue(value.contains("LOWER(" + column + ")"),
                    "降级查询必须保留 " + column + " 的 LIKE 匹配");
        }
        assertTrue(value.contains(":isAscii = true"), "拼音条件必须受 :isAscii 开关保护");
        assertTrue(value.contains(":pinyinKeyword"), "降级查询必须支持拼音列匹配");
        assertTrue(value.contains("b.deletedAt IS NULL"), "必须过滤回收站书籍");
    }

    @Test
    void pinyinBackfillQueryIsCursored() throws NoSuchMethodException {
        Method method = BookRepository.class.getMethod(
                "findPinyinBackfillCandidates",
                Long.class, org.springframework.data.domain.Pageable.class);
        String value = method.getAnnotation(Query.class).value();
        assertTrue(value.contains("b.searchPinyin IS NULL"), "只回填拼音为空的书籍");
        assertTrue(value.contains("ORDER BY b.id"), "必须按主键排序保证游标分页稳定");
    }

    private Set<String> namedParameters(String query) {
        Set<String> parameters = new LinkedHashSet<>();
        Matcher matcher = NAMED_PARAMETER.matcher(query);
        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }
        return parameters;
    }
}
