package com.aibook.service;

import com.aibook.dto.AuthorDTO;
import com.aibook.dto.AuthorPageDTO;
import com.aibook.dto.AuthorPageDTO.AuthorSummaryDTO;
import com.aibook.dto.AuthorRequest;
import com.aibook.model.entity.Author;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.AuthorRepository;
import com.aibook.repository.BookRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 作者登记、历史补齐和管理服务。 */
@Service
@RequiredArgsConstructor
public class AuthorService {

    private static final Pattern AUTHOR_SEPARATOR = Pattern.compile("\\s*[,，、;；]+\\s*");
    private static final Set<String> UNKNOWN_AUTHORS = Set.of(
            "未知", "未知作者", "佚名", "unknown", "unknown author");

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    /** 分页读取作者；历史关联补齐由启动迁移处理，不进入列表请求链路。 */
    @Transactional(readOnly = true)
    public AuthorPageDTO getAuthors(
            User user, int page, int size, String keyword, String sort) {
        Page<Author> authorPage = authorRepository.findPage(
                user,
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(
                        Math.max(0, page),
                        Math.max(1, Math.min(size, 100)),
                        authorSort(sort)));
        List<Long> authorIds = authorPage.getContent().stream().map(Author::getId).toList();
        Map<Long, Long> bookCounts = (authorIds.isEmpty()
                ? List.<AuthorRepository.AuthorBookCount>of()
                : authorRepository.countActiveBooksByAuthorIds(user, authorIds)).stream()
                .collect(Collectors.toMap(
                        AuthorRepository.AuthorBookCount::getAuthorId,
                        AuthorRepository.AuthorBookCount::getBookCount));
        List<AuthorDTO> content = authorPage.getContent().stream()
                .map(author -> toDTO(author, bookCounts.getOrDefault(author.getId(), 0L)))
                .toList();
        AuthorRepository.AuthorRelationStatistics relations =
                authorRepository.summarizeActiveBooks(user);
        return new AuthorPageDTO(
                content,
                authorPage.getTotalElements(),
                authorPage.getTotalPages(),
                authorPage.getNumber(),
                authorPage.getSize(),
                new AuthorSummaryDTO(
                        authorRepository.countByUser(user),
                        relations == null ? 0 : relations.getActiveAuthorCount(),
                        relations == null ? 0 : relations.getRelatedBookCount()));
    }

    /** 手动新增单个作者。 */
    @Transactional
    public AuthorDTO createAuthor(User user, AuthorRequest request) {
        String name = cleanName(request.getName());
        String normalizedName = normalize(name);
        if (name == null || UNKNOWN_AUTHORS.contains(normalizedName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的作者名称");
        }
        int inserted = authorRepository.insertIfAbsent(
                name, normalizedName, user.getId());
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "作者已存在");
        }
        return toDTO(authorRepository.findByUserAndNormalizedName(user, normalizedName)
                .orElseThrow(() -> new IllegalStateException("新增作者后未能读取记录")), 0L);
    }

    /** 将书籍的作者字符串规范化为作者记录及书籍关联。 */
    @Transactional
    public void synchronizeBook(Book book) {
        if (book == null || book.getUser() == null) return;
        LinkedHashSet<Author> recognizedAuthors = new LinkedHashSet<>();
        String authorText = cleanName(book.getAuthor());
        if (authorText != null) {
            for (String candidate : AUTHOR_SEPARATOR.split(authorText)) {
                String name = cleanName(candidate);
                String normalizedName = normalize(name);
                if (name == null || UNKNOWN_AUTHORS.contains(normalizedName)) continue;
                Author author = findOrCreate(book.getUser(), name, normalizedName);
                recognizedAuthors.add(author);
            }
        }
        if (!sameAuthorIds(book.getAuthors(), recognizedAuthors)) {
            book.setAuthors(recognizedAuthors);
            bookRepository.save(book);
        }
    }

    private Author findOrCreate(User user, String name, String normalizedName) {
        return authorRepository.findByUserAndNormalizedName(user, normalizedName)
                .orElseGet(() -> {
                    authorRepository.insertIfAbsent(name, normalizedName, user.getId());
                    return authorRepository.findByUserAndNormalizedName(user, normalizedName)
                            .orElseThrow(() -> new IllegalStateException("登记作者后未能读取记录"));
                });
    }

    private boolean sameAuthorIds(Set<Author> current, Set<Author> expected) {
        if (current == null || current.size() != expected.size()) return false;
        return current.stream().map(Author::getId).collect(java.util.stream.Collectors.toSet())
                .equals(expected.stream().map(Author::getId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private AuthorDTO toDTO(Author author, long bookCount) {
        return AuthorDTO.builder()
                .id(author.getId())
                .name(author.getName())
                .bookCount(bookCount)
                .createdAt(author.getCreatedAt())
                .build();
    }

    private Sort authorSort(String value) {
        Sort.Order order = switch (value == null ? "" : value) {
            case "NAME_DESC" -> Sort.Order.desc("normalizedName");
            case "CREATED_DESC" -> Sort.Order.desc("createdAt");
            case "CREATED_ASC" -> Sort.Order.asc("createdAt");
            default -> Sort.Order.asc("normalizedName");
        };
        return Sort.by(order, Sort.Order.asc("id"));
    }

    private String cleanName(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
