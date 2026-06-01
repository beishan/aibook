package com.aibook.service;

import com.aibook.model.entity.Tag;
import com.aibook.model.entity.User;
import com.aibook.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签服务
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    /**
     * 获取用户所有标签
     */
    public List<Tag> getTags(User user) {
        return tagRepository.findByUser(user);
    }

    /**
     * 创建标签
     */
    @Transactional
    public Tag createTag(User user, String name, String color) {
        Tag existing = tagRepository.findByNameAndUser(name, user);
        if (existing != null) {
            throw new RuntimeException("标签已存在");
        }

        Tag tag = Tag.builder()
                .name(name)
                .color(color)
                .user(user)
                .build();

        return tagRepository.save(tag);
    }

    /**
     * 更新标签
     */
    @Transactional
    public Tag updateTag(Long id, String name, String color, User user) {
        Tag tag = tagRepository.findById(id)
                .filter(t -> t.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("标签不存在"));

        tag.setName(name);
        tag.setColor(color);

        return tagRepository.save(tag);
    }

    /**
     * 删除标签
     */
    @Transactional
    public void deleteTag(Long id, User user) {
        Tag tag = tagRepository.findById(id)
                .filter(t -> t.getUser().equals(user))
                .orElseThrow(() -> new RuntimeException("标签不存在"));

        tagRepository.delete(tag);
    }
}
