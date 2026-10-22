package com.aibook.service;

import com.aibook.dto.recommendation.RecommendationFilter;
import com.aibook.dto.recommendation.RecommendationRuleRequest;
import com.aibook.model.entity.RecommendationBatch;
import com.aibook.model.entity.RecommendationRule;
import com.aibook.model.entity.User;
import com.aibook.repository.RecommendationBatchRepository;
import com.aibook.repository.RecommendationRuleRepository;
import com.aibook.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final RecommendationRuleRepository rules;
    private final RecommendationBatchRepository batches;
    private final RecommendationCandidates candidates;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public record RuleView(Long id, String name, boolean enabled, int sortOrder,
            RecommendationFilter filter) {}
    public record GroupView(RuleView rule, LocalDate date, LocalDateTime generatedAt,
            int candidateCount, int repeatedCount, int invalidCount,
            List<Map<String, Object>> books) {}
    public record TodayView(LocalDate date, List<GroupView> groups) {}

    private LocalDate today() {
        return LocalDate.now(ZONE);
    }

    @Transactional
    public List<RuleView> rules(User user) {
        return rules.ownedIds(user.getId()).stream().map(id -> view(lock(user, id)))
                .sorted(Comparator.comparingInt(RuleView::sortOrder).thenComparing(RuleView::id))
                .toList();
    }

    @Transactional
    public RuleView save(User user, Long id, RecommendationRuleRequest request) {
        validate(request);
        RecommendationRule rule;
        if (id == null) {
            // Serialize creation per account, including the group limit.
            users.findByIdForLogPolicyUpdate(user.getId()).orElseThrow();
            if (rules.ownedIds(user.getId()).size() >= 30) bad("每个账户最多创建 30 组推荐规则");
            rule = new RecommendationRule();
            rule.setUser(user);
        } else {
            rule = lock(user, id);
        }
        String filter = json(request.filter());
        boolean changed = !filter.equals(rule.getFilterJson());
        rule.setName(request.name().trim());
        rule.setEnabled(request.enabled());
        rule.setSortOrder(request.sortOrder());
        rule.setFilterJson(filter);
        rules.saveAndFlush(rule);
        // Pure naming/sorting edits retain the day's selected books.
        if (changed && rule.isEnabled()) generate(user, rule, today(), true);
        return view(rule);
    }

    @Transactional
    public void delete(User user, Long id) {
        rules.delete(lock(user, id));
    }

    @Transactional(readOnly = true)
    public int preview(User user, RecommendationRuleRequest request) {
        validate(request);
        return candidates.ids(user, request.filter(), today()).size();
    }

    @Transactional
    public TodayView today(User user) {
        LocalDate date = today();
        var groups = new ArrayList<GroupView>();
        // Acquire rule locks in ID order so concurrent tabs cannot generate duplicate batches.
        for (Long id : rules.ownedIds(user.getId())) {
            var rule = lock(user, id);
            if (rule.isEnabled()) groups.add(group(user, rule, generate(user, rule, date, false)));
        }
        groups.sort(Comparator.comparingInt((GroupView g) -> g.rule().sortOrder())
                .thenComparing(g -> g.rule().id()));
        return new TodayView(date, groups);
    }

    @Transactional
    public GroupView reroll(User user, Long id) {
        var rule = lock(user, id);
        if (!rule.isEnabled()) bad("请先启用该组规则");
        return group(user, rule, generate(user, rule, today(), true));
    }

    private RecommendationRule lock(User user, Long id) {
        return rules.lockOwned(id, user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐规则不存在"));
    }

    private RecommendationBatch generate(User user, RecommendationRule rule,
            LocalDate date, boolean force) {
        if (!force) {
            var existing = batches.findFirstByRuleIdAndRecommendationDateOrderByIdDesc(rule.getId(), date);
            if (existing.isPresent()
                    && existing.get().getFilterSnapshot().equals(rule.getFilterJson())) return existing.get();
        }
        var filter = read(rule.getFilterJson(), RecommendationFilter.class);
        var pool = candidates.ids(user, filter, date);
        var seen = new HashMap<Long, LocalDateTime>();
        for (var batch : batches.findByRuleIdAndRecommendationDateGreaterThanEqual(
                rule.getId(), date.minusDays(filter.avoidDays()))) {
            for (Long bookId : ids(batch)) {
                seen.merge(bookId, batch.getGeneratedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<Long> selected = select(pool, seen, filter.count(), filter.strictAvoid());
        var batch = new RecommendationBatch();
        batch.setRule(rule);
        batch.setRecommendationDate(date);
        batch.setGeneratedAt(LocalDateTime.now(ZONE));
        batch.setFilterSnapshot(rule.getFilterJson());
        batch.setBookIdsJson(json(selected));
        batch.setCandidateCount(pool.size());
        batch.setRepeatedCount((int) selected.stream().filter(seen::containsKey).count());
        return batches.saveAndFlush(batch);
    }

    /** Uniform random fresh selection; fallback chooses the least recently recommended first. */
    static List<Long> select(List<Long> pool, Map<Long, LocalDateTime> seen,
            int count, boolean strict) {
        var shuffled = new ArrayList<>(new LinkedHashSet<>(pool));
        Collections.shuffle(shuffled);
        var selected = new ArrayList<Long>();
        shuffled.stream().filter(id -> !seen.containsKey(id)).limit(count).forEach(selected::add);
        if (!strict && selected.size() < count) {
            shuffled.stream().filter(seen::containsKey)
                    .sorted(Comparator.comparing(seen::get))
                    .limit(count - selected.size()).forEach(selected::add);
        }
        return selected;
    }

    private GroupView group(User user, RecommendationRule rule, RecommendationBatch batch) {
        var ids = ids(batch);
        var found = candidates.books(user, ids);
        var byId = new HashMap<Long, Map<String, Object>>();
        found.forEach(book -> byId.put(((Number) book.get("id")).longValue(), book));
        var books = ids.stream().filter(byId::containsKey).map(byId::get).toList();
        return new GroupView(view(rule), batch.getRecommendationDate(), batch.getGeneratedAt(),
                batch.getCandidateCount(), batch.getRepeatedCount(), ids.size() - books.size(), books);
    }

    private RuleView view(RecommendationRule rule) {
        return new RuleView(rule.getId(), rule.getName(), rule.isEnabled(), rule.getSortOrder(),
                read(rule.getFilterJson(), RecommendationFilter.class));
    }

    private List<Long> ids(RecommendationBatch batch) {
        try {
            return mapper.readValue(batch.getBookIdsJson(), new TypeReference<List<Long>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("读取推荐结果失败", ex);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("推荐规则格式不正确", ex);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("读取推荐规则失败", ex);
        }
    }

    static void validate(RecommendationRuleRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().trim().length() > 80 || request.filter() == null) {
            bad("请填写推荐组名称（1–80 字）和规则");
        }
        var f = request.filter();
        if (request.sortOrder() < 0 || request.sortOrder() > 9999
                || f.count() < 1 || f.count() > 30 || f.avoidDays() < 0 || f.avoidDays() > 365) {
            bad("推荐数量须为 1–30，去重天数须为 0–365，排序须为 0–9999");
        }
        for (var list : Arrays.asList(f.categoryIds(), f.tagIds(), f.excludedTagIds(),
                f.authorIds(), f.shelfGroupIds())) {
            if (list != null && (list.size() > 500 || list.stream().anyMatch(id -> id == null || id < 1))) {
                bad("筛选项编号无效或超过 500 项");
            }
        }
        allowed(f.statuses(), Set.of("UNREADING", "READING", "FINISHED"));
        allowed(f.formats(), Set.of("epub", "txt", "pdf", "mobi", "azw3", "docx", "html", "md"));
        allowed(f.sources(), Set.of("UPLOAD", "DIRECTORY_SCAN", "CRAWLER"));
        for (Integer days : Arrays.asList(f.addedWithinDays(), f.addedBeforeDays(), f.notReadDays())) {
            if (days != null && (days < 1 || days > 36500)) bad("时间筛选须为 1–36500 天或留空");
        }
        if (f.addedWithinDays() != null && f.addedBeforeDays() != null
                && f.addedBeforeDays() >= f.addedWithinDays()) bad("入库时间范围没有交集");
    }

    private static void allowed(List<String> values, Set<String> allowed) {
        if (values != null && (values.size() > allowed.size()
                || values.stream().anyMatch(v -> v == null || !allowed.contains(v)))) bad("筛选项无效");
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
