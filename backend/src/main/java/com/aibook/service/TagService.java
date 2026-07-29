package com.aibook.service;

import com.aibook.dto.TagDTO;
import com.aibook.model.entity.Tag;
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

    public TagDTO convertToDTO(Tag tag, User user) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .bookCount(bookRepository.countByUserAndTagsContaining(user, tag))
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
