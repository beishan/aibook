package com.aibook.service;

import com.aibook.dto.CategoryDTO;
import com.aibook.dto.CategoryMoveRequest;
import com.aibook.dto.CategoryReorderRequest;
import com.aibook.dto.CategoryRequest;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.repository.ScanDirectoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分类服务。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final int MAX_DEPTH = 3;

    private static final Map<String, List<String>> PRESET_CATEGORIES = createPresetCategories();

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final ScanDirectoryRepository scanDirectoryRepository;

    /**
     * 获取用户所有分类。空分类库会自动初始化常见分类。
     */
    @Transactional
    public List<CategoryDTO> getCategories(User user) {
        initializeIfEmpty(user);
        return categoryRepository.findByUserOrderBySortOrderAscNameAsc(user).stream()
                .map(category -> toDTO(category, false))
                .toList();
    }

    /**
     * 获取分类树和聚合书籍数。
     */
    @Transactional
    public List<CategoryDTO> getCategoryTree(User user) {
        initializeIfEmpty(user);
        List<Category> categories = categoryRepository.findByUserOrderBySortOrderAscNameAsc(user);
        Map<Long, CategoryDTO> dtoById = new LinkedHashMap<>();
        List<CategoryDTO> roots = new ArrayList<>();

        for (Category category : categories) {
            dtoById.put(category.getId(), toDTO(category, true));
        }

        for (Category category : categories) {
            CategoryDTO dto = dtoById.get(category.getId());
            if (category.getParent() == null) {
                roots.add(dto);
            } else {
                CategoryDTO parent = dtoById.get(category.getParent().getId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                }
            }
        }

        roots.forEach(this::calculateAggregateBookCount);
        return roots;
    }

    public List<CategoryDTO> getSubCategories(User user, Long parentId) {
        Category parent = getOwnedCategory(parentId, user);
        return categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, parent).stream()
                .map(category -> toDTO(category, false))
                .toList();
    }

    /**
     * 创建分类。
     */
    @Transactional
    public CategoryDTO createCategory(User user, CategoryRequest request) {
        String name = normalizeName(request.getName());
        Category parent = resolveParent(request.getParentId(), user);
        ensureNameAvailable(user, parent, name, null);
        ensureParentDepth(parent);

        Category category = Category.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .parent(parent)
                .sortOrder(request.getSortOrder() == null ? nextSortOrder(user, parent) : request.getSortOrder())
                .builtIn(false)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .user(user)
                .build();

        return toDTO(categoryRepository.save(category), false);
    }

    /**
     * 更新分类基础信息及父分类。
     */
    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryRequest request, User user) {
        Category category = getOwnedCategory(id, user);
        String name = normalizeName(request.getName());
        Category parent = resolveParent(request.getParentId(), user);
        validateMove(category, parent, user);
        ensureNameAvailable(user, parent, name, id);

        category.setName(name);
        category.setDescription(trimToNull(request.getDescription()));
        category.setParent(parent);
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getEnabled() != null) {
            category.setEnabled(request.getEnabled());
        }

        return toDTO(categoryRepository.save(category), false);
    }

    /**
     * 移动分类。
     */
    @Transactional
    public CategoryDTO moveCategory(Long id, CategoryMoveRequest request, User user) {
        Category category = getOwnedCategory(id, user);
        Category parent = resolveParent(request.getParentId(), user);
        validateMove(category, parent, user);
        ensureNameAvailable(user, parent, category.getName(), id);

        category.setParent(parent);
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        return toDTO(categoryRepository.save(category), false);
    }

    /**
     * 批量保存同级显示顺序。
     */
    @Transactional
    public void reorderCategories(CategoryReorderRequest request, User user) {
        for (CategoryReorderRequest.Item item : request.getItems()) {
            if (item.getId() == null || item.getSortOrder() == null) {
                throw badRequest("分类ID和排序值不能为空");
            }
            Category category = getOwnedCategory(item.getId(), user);
            category.setSortOrder(item.getSortOrder());
            categoryRepository.save(category);
        }
    }

    /**
     * 安全删除分类。分类下书籍会转移到目标分类；未指定目标时转为未分类。
     */
    @Transactional
    public void deleteCategory(Long id, Long targetCategoryId, User user) {
        Category category = getOwnedCategory(id, user);
        if (!categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, category).isEmpty()) {
            throw badRequest("请先移动或删除该分类的子分类");
        }

        Category target = resolveTransferTarget(targetCategoryId, category, user);
        transferBooks(category, target, user);
        transferScanDirectoryDefaults(category, target, user);
        categoryRepository.delete(category);
    }

    /**
     * 将源分类的书籍和子分类合并到目标分类，并删除源分类。
     */
    @Transactional
    public CategoryDTO mergeCategory(Long sourceId, Long targetId, User user) {
        if (targetId == null) {
            throw badRequest("目标分类不能为空");
        }
        Category source = getOwnedCategory(sourceId, user);
        Category target = getOwnedCategory(targetId, user);
        if (Objects.equals(source.getId(), target.getId())) {
            throw badRequest("不能合并到当前分类");
        }
        if (isDescendant(target, source)) {
            throw badRequest("不能把分类合并到它的子分类");
        }

        List<Category> children =
                categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, source);
        for (Category child : children) {
            ensureNameAvailable(user, target, child.getName(), child.getId());
            child.setParent(target);
            categoryRepository.save(child);
        }

        transferBooks(source, target, user);
        transferScanDirectoryDefaults(source, target, user);
        categoryRepository.delete(source);
        return toDTO(target, false);
    }

    /**
     * 幂等补齐系统预置分类。
     */
    @Transactional
    public List<CategoryDTO> initializePresets(User user) {
        int rootOrder = 0;
        for (Map.Entry<String, List<String>> entry : PRESET_CATEGORIES.entrySet()) {
            Category root = findOrCreatePreset(user, null, entry.getKey(), rootOrder++);
            int childOrder = 0;
            for (String childName : entry.getValue()) {
                findOrCreatePreset(user, root, childName, childOrder++);
            }
        }
        return getCategoryTree(user);
    }

    public Category getOwnedCategory(Long id, User user) {
        return categoryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分类不存在"));
    }

    /**
     * 返回分类自身及所有后代 ID。
     */
    public List<Long> getCategoryAndDescendantIds(Long categoryId, User user) {
        Category category = getOwnedCategory(categoryId, user);
        List<Long> ids = new ArrayList<>();
        collectDescendantIds(category, user, ids);
        return ids;
    }

    private void initializeIfEmpty(User user) {
        if (categoryRepository.countByUser(user) == 0) {
            initializePresets(user);
        }
    }

    private Category findOrCreatePreset(
            User user, Category parent, String name, int sortOrder) {
        List<Category> siblings =
                categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, parent);
        Category existing = siblings.stream()
                .filter(category -> category.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        return categoryRepository.save(Category.builder()
                .name(name)
                .description("系统预置分类")
                .parent(parent)
                .sortOrder(sortOrder)
                .builtIn(true)
                .enabled(true)
                .user(user)
                .build());
    }

    private Category resolveParent(Long parentId, User user) {
        return parentId == null ? null : getOwnedCategory(parentId, user);
    }

    private Category resolveTransferTarget(Long targetId, Category source, User user) {
        if (targetId == null) {
            return null;
        }
        Category target = getOwnedCategory(targetId, user);
        if (Objects.equals(source.getId(), target.getId())) {
            throw badRequest("目标分类不能是当前分类");
        }
        return target;
    }

    private void transferBooks(Category source, Category target, User user) {
        List<Book> books = bookRepository.findByUserAndCategory(user, source);
        books.forEach(book -> book.setCategory(target));
        bookRepository.saveAll(books);
    }

    private void transferScanDirectoryDefaults(Category source, Category target, User user) {
        var directories = scanDirectoryRepository.findByUserAndDefaultCategory(user, source);
        directories.forEach(directory -> directory.setDefaultCategory(target));
        scanDirectoryRepository.saveAll(directories);
    }

    private void validateMove(Category category, Category parent, User user) {
        if (parent != null && Objects.equals(category.getId(), parent.getId())) {
            throw badRequest("分类不能成为自己的父分类");
        }
        if (parent != null && isDescendant(parent, category)) {
            throw badRequest("分类不能移动到它的子分类下");
        }

        int parentDepth = parent == null ? 0 : depth(parent);
        int subtreeDepth = subtreeDepth(category, user);
        if (parentDepth + subtreeDepth > MAX_DEPTH) {
            throw badRequest("分类层级最多支持" + MAX_DEPTH + "层");
        }
    }

    private void ensureParentDepth(Category parent) {
        if (parent != null && depth(parent) >= MAX_DEPTH) {
            throw badRequest("分类层级最多支持" + MAX_DEPTH + "层");
        }
    }

    private int depth(Category category) {
        int depth = 1;
        Category current = category;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private int subtreeDepth(Category category, User user) {
        int maxChildDepth = 0;
        for (Category child :
                categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, category)) {
            maxChildDepth = Math.max(maxChildDepth, subtreeDepth(child, user));
        }
        return 1 + maxChildDepth;
    }

    private boolean isDescendant(Category candidate, Category ancestor) {
        Category current = candidate;
        while (current != null) {
            if (Objects.equals(current.getId(), ancestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void ensureNameAvailable(
            User user, Category parent, String name, Long excludedId) {
        boolean exists = excludedId == null
                ? categoryRepository.existsByUserAndParentAndNameIgnoreCase(user, parent, name)
                : categoryRepository.existsByUserAndParentAndNameIgnoreCaseAndIdNot(
                        user, parent, name, excludedId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同级分类名称已存在");
        }
    }

    private int nextSortOrder(User user, Category parent) {
        return categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, parent).stream()
                .map(Category::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private CategoryDTO toDTO(Category category, boolean includeCounts) {
        long directBookCount = includeCounts
                ? bookRepository.countByUserAndCategory(category.getUser(), category)
                : 0;
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .sortOrder(category.getSortOrder())
                .builtIn(Boolean.TRUE.equals(category.getBuiltIn()))
                .enabled(!Boolean.FALSE.equals(category.getEnabled()))
                .directBookCount(directBookCount)
                .bookCount(directBookCount)
                .children(new ArrayList<>())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private long calculateAggregateBookCount(CategoryDTO category) {
        long total = category.getDirectBookCount();
        for (CategoryDTO child : category.getChildren()) {
            total += calculateAggregateBookCount(child);
        }
        category.setBookCount(total);
        return total;
    }

    private void collectDescendantIds(Category category, User user, List<Long> ids) {
        ids.add(category.getId());
        for (Category child :
                categoryRepository.findByUserAndParentOrderBySortOrderAscNameAsc(user, category)) {
            collectDescendantIds(child, user, ids);
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw badRequest("分类名称不能为空");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static Map<String, List<String>> createPresetCategories() {
        Map<String, List<String>> presets = new LinkedHashMap<>();
        presets.put("网络文学", List.of(
                "玄幻", "奇幻", "武侠", "仙侠", "修真", "都市", "言情", "历史架空",
                "军事", "科幻", "悬疑", "推理", "恐怖", "游戏", "体育", "轻小说",
                "同人", "穿越", "重生"));
        presets.put("文学", List.of(
                "中国文学", "外国文学", "古典文学", "当代文学", "小说", "散文", "诗歌", "戏剧"));
        presets.put("人文社科", List.of(
                "历史", "哲学", "心理学", "社会学", "政治", "法律", "宗教", "文化"));
        presets.put("经济管理", List.of("经济", "金融", "投资", "管理", "营销", "创业", "职场"));
        presets.put("科技", List.of(
                "计算机", "编程开发", "人工智能", "互联网", "数学", "物理", "生物",
                "医学", "工程技术", "科普"));
        presets.put("生活", List.of("健康", "美食", "旅行", "家居", "育儿", "两性情感", "个人成长"));
        presets.put("教育", List.of("教材", "考试", "外语", "工具书", "学习方法"));
        presets.put("艺术", List.of("绘画", "摄影", "音乐", "设计", "影视"));
        presets.put("其他", List.of("漫画", "少儿", "杂志", "未分类"));
        return presets;
    }
}
