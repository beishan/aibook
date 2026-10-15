package com.aibook.service;

import com.aibook.dto.TagDTO;
import com.aibook.dto.TagLibraryScanPageDTO;
import com.aibook.model.entity.Tag;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 标签服务
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final BookRepository bookRepository;

    /**
     * 获取用户所有标签
     */
    @Transactional(readOnly = true)
    public List<TagDTO> getTags(User user) {
        return tagRepository.findByUserOrderByNameAsc(user).stream()
                .map(tag -> convertToDTO(tag, user))
                .toList();
    }

    /**
     * 创建标签
     */
    @Transactional
    public TagDTO createTag(User user, String name, String color) {
        String normalizedName = normalizeName(name);
        Tag existing = tagRepository.findByNameIgnoreCaseAndUser(normalizedName, user);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "标签已存在");
        }

        Tag tag = Tag.builder()
                .name(normalizedName)
                .color(normalizeColor(color))
                .sourceType(Tag.SourceType.MANUAL)
                .user(user)
                .build();

        return convertToDTO(tagRepository.save(tag), user);
    }

    /**
     * 更新标签
     */
    @Transactional
    public TagDTO updateTag(Long id, String name, String color, User user) {
        Tag tag = getOwnedTag(id, user);
        String normalizedName = normalizeName(name);
        Tag duplicate = tagRepository.findByNameIgnoreCaseAndUser(normalizedName, user);
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "标签已存在");
        }

        tag.setName(normalizedName);
        tag.setColor(normalizeColor(color));

        return convertToDTO(tagRepository.save(tag), user);
    }

    /**
     * 删除标签
     */
    @Transactional
    public void deleteTag(Long id, User user) {
        Tag tag = getOwnedTag(id, user);

        tagRepository.deleteBookAssociations(id);
        tagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public Tag getOwnedTag(Long id, User user) {
        return tagRepository.findById(id)
                .filter(tag -> tag.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "标签不存在"));
    }

    @Transactional(readOnly = true)
    public Set<Tag> getOwnedTags(List<Long> tagIds, User user) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Long> distinctIds = tagIds.stream().distinct().toList();
        List<Tag> tags = tagRepository.findAllById(distinctIds).stream()
                .filter(tag -> tag.getUser().getId().equals(user.getId()))
                .toList();
        if (tags.size() != distinctIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "部分标签不存在或无权访问");
        }
        return new LinkedHashSet<>(tags);
    }

    /** 按外部元信息查找或创建当前用户的标签。 */
    @Transactional
    public Set<Tag> getOrCreateTags(List<String> names, User user) {
        return getOrCreateTags(names, user, null);
    }

    /** 按刮削器名称创建标签，并保留标签首次来源。 */
    @Transactional
    public Set<Tag> getOrCreateTags(List<String> names, User user, String scraperName) {
        if (names == null || names.isEmpty()) return new LinkedHashSet<>();
        LinkedHashSet<String> normalizedNames = names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .filter(name -> name.length() <= 120)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Tag> result = new LinkedHashSet<>();
        for (String name : normalizedNames) {
            Tag tag = tagRepository.findByNameIgnoreCaseAndUser(name, user);
            if (tag == null) {
                tag = tagRepository.save(Tag.builder()
                        .name(name)
                        .color("#64748B")
                        .sourceType(scraperName == null || scraperName.isBlank()
                                ? Tag.SourceType.MANUAL : Tag.SourceType.SCRAPER)
                        .sourceDetail(scraperName == null || scraperName.isBlank()
                                ? null : scraperName.trim())
                        .user(user)
                        .build());
            }
            result.add(tag);
        }
        return result;
    }

    /** 扫描书库中指定页的书籍标签，供前端逐页展示真实扫描进度。 */
    @Transactional(readOnly = true)
    public TagLibraryScanPageDTO scanLibraryTags(User user, int page, int size) {
        Page<Book> books = bookRepository.findAllForTagLibraryScan(
                user, PageRequest.of(page, size));
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        books.getContent().forEach(book -> book.getTags().stream()
                .map(Tag::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(names::add));
        return new TagLibraryScanPageDTO(
                List.copyOf(names), Math.min((long) (page + 1) * size, books.getTotalElements()),
                books.getTotalElements(),
                !books.hasNext());
    }

    /** 将用户选中的书库标签添加到标签库。 */
    @Transactional
    public List<TagDTO> addScannedTags(List<String> names, User user) {
        if (names == null || names.isEmpty()) return List.of();
        LinkedHashSet<String> normalizedNames = names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .filter(name -> name.length() <= 120)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String name : normalizedNames) {
            Tag existing = tagRepository.findByNameIgnoreCaseAndUser(name, user);
            if (existing == null) {
                tagRepository.save(Tag.builder()
                        .name(name)
                        .color("#64748B")
                        .sourceType(Tag.SourceType.LIBRARY_SCAN)
                        .user(user)
                        .build());
            }
        }
        return tagRepository.findByUserOrderByNameAsc(user).stream()
                .filter(tag -> normalizedNames.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(tag.getName())))
                .map(tag -> convertToDTO(tag, user))
                .toList();
    }

    /** 批量删除前验证所有标签属于当前账户。 */
    @Transactional
    public int deleteTags(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Long> distinctIds = ids.stream().filter(id -> id != null).distinct().toList();
        Set<Tag> tags = getOwnedTags(distinctIds, user);
        for (Tag tag : tags) {
            tagRepository.deleteBookAssociations(tag.getId());
        }
        tagRepository.deleteAll(tags);
        return tags.size();
    }

    public TagDTO convertToDTO(Tag tag, User user) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .sourceType(tag.getSourceType() == null
                        ? Tag.SourceType.MANUAL.name() : tag.getSourceType().name())
                .sourceDetail(tag.getSourceDetail())
                .bookCount(bookRepository.countByUserAndTagsContainingAndDeletedAtIsNull(
                        user, tag))
                .createdAt(tag.getCreatedAt())
                .build();
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签名称不能为空");
        }
        return name.trim();
    }

    private String normalizeColor(String color) {
        return color == null || color.isBlank() ? "#64748B" : color.toUpperCase();
    }
}
