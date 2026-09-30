package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteProject;
import com.aibook.model.entity.RewriteRevision;
import com.aibook.model.entity.User;
import com.aibook.repository.LibraryChapterRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.RewriteChapterRepository;
import com.aibook.repository.RewriteProjectRepository;
import com.aibook.repository.RewriteRevisionRepository;
import com.aibook.service.conversion.EpubTextExtractor;
import com.aibook.dto.ConversionChapterDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Owns the editable draft. Completed publications are materialized as immutable chapters. */
@Service
@RequiredArgsConstructor
public class RewriteService {

    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_CHAPTER_CHARS = 25_000_000;

    private final BookService bookService;
    private final BookVersionService versionService;
    private final BookVersionRepository versionRepository;
    private final LibraryChapterRepository libraryChapters;
    private final RewriteProjectRepository projects;
    private final RewriteChapterRepository chapters;
    private final RewriteRevisionRepository revisions;
    private final TxtParserService txtParser;
    private final EpubTextExtractor epubExtractor;
    private final ObjectMapper objectMapper;

    private final Cache<String, PreviewData> previews = Caffeine.newBuilder()
            .maximumWeight(80 * 1024 * 1024)
            .weigher((String key, PreviewData value) -> Math.max(1,
                    value.chapters().stream().mapToInt(chapter ->
                            chapter.content().length() * 2).sum()))
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build();

    public record ChapterData(String title, String content, String sourceKey) { }
    public record ChapterOverride(String title, boolean mergeWithPrevious) { }

    private record FinalChapter(String title, String content, String sourceKey,
                                String sourceTitle, String sourceContent) { }

    private record PreviewData(Long userId, Long bookId, Long versionId,
                               List<ChapterData> chapters) { }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(User user, Long bookId, Long versionId) {
        Book book = bookService.getBookEntity(bookId, user);
        BookVersion version = versionService.resolveVersion(book, versionId);
        List<ChapterData> parsed = parse(version);
        if (parsed.isEmpty()) {
            throw badRequest("源版本没有可读取的正文");
        }
        long totalCharacters = parsed.stream().mapToLong(chapter ->
                chapter.content().length()).sum();
        if (totalCharacters > MAX_CHAPTER_CHARS) {
            throw badRequest("源版本提取后的正文超过 2500 万字符上限");
        }
        String token = UUID.randomUUID().toString();
        previews.put(token, new PreviewData(user.getId(), bookId, version.getId(), parsed));
        return Map.of(
                "previewToken", token,
                "bookId", bookId,
                "sourceVersionId", version.getId(),
                "chapterCount", parsed.size(),
                "singleChapterAvailable", parsed.size() > 1,
                "chapters", parsed.stream().map(chapter -> Map.of(
                        "title", chapter.title(),
                        "length", chapter.content().length(),
                        "excerpt", chapter.content().substring(0,
                                Math.min(120, chapter.content().length())))).toList());
    }

    @Transactional
    public Map<String, Object> create(User user, String token, String name,
                                       String versionName, String description,
                                       boolean singleChapter,
                                       List<ChapterOverride> overrides) {
        RewriteProject previous = projects.findByCreationTokenAndUser(token, user).orElse(null);
        if (previous != null) return summary(previous);
        PreviewData parsed = previews.getIfPresent(token);
        if (parsed == null || !Objects.equals(parsed.userId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.GONE, "预览已过期，请重新预览源版本");
        }
        if (name == null || name.isBlank() || versionName == null || versionName.isBlank()) {
            throw badRequest("项目名和版本名不能为空");
        }
        if (name.length() > 200 || versionName.length() > 255
                || (description != null && description.length() > 10_000)) {
            throw badRequest("项目名称、版本名称或说明超出长度限制");
        }
        Book book = bookService.getBookEntity(parsed.bookId(), user);
        BookVersion source = versionService.resolveVersion(book, parsed.versionId());
        if (!Set.of("structured", "txt", "epub").contains(source.getFormat().toLowerCase())) {
            throw badRequest("源版本格式不支持重写");
        }
        List<FinalChapter> sourceChapters = finalizeChapters(parsed.chapters(),
                overrides, singleChapter);
        if (sourceChapters.stream().anyMatch(chapter ->
                chapter.content().length() > MAX_CHAPTER_CHARS)) {
            throw badRequest("某章节超过 2500 万字符，请调整章节边界后重试");
        }
        BookVersion rewrite = new BookVersion();
        rewrite.setBook(book);
        rewrite.setDisplayName(versionName.strip());
        rewrite.setFormat("structured");
        rewrite.setFilePath("rewrite:" + UUID.randomUUID());
        rewrite.setFileSize(0L);
        rewrite.setPrimaryVersion(false);
        rewrite.setChapterCount(sourceChapters.size());
        rewrite.setSourceType("REWRITE");
        rewrite = versionRepository.save(rewrite);

        RewriteProject project = new RewriteProject();
        project.setUser(user);
        project.setBook(book);
        project.setSourceVersion(source);
        project.setRewriteVersion(rewrite);
        project.setCreationToken(token);
        project.setName(name.strip());
        project.setDescription(description == null ? null : description.strip());
        project = projects.save(project);
        rewrite.setSourceId(String.valueOf(project.getId()));

        int index = 0;
        for (FinalChapter original : sourceChapters) {
            RewriteChapter chapter = new RewriteChapter();
            chapter.setProject(project);
            chapter.setSortIndex(index++);
            chapter.setTitle(original.title());
            chapter.setContent(original.content());
            chapter.setSourceKey(original.sourceKey());
            chapter.setSourceTitle(original.sourceTitle());
            chapter.setSourceContent(original.sourceContent());
            chapter.setWordCount(wordCount(original.content()));
            chapters.save(chapter);
        }
        return summary(project);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(User user, int page, int size,
                                           RewriteProject.Status status, String keyword) {
        PageRequest pageable = PageRequest.of(Math.max(0, page),
                Math.max(1, Math.min(50, size)),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        String query = keyword == null ? "" : keyword.strip();
        if (query.length() > 100) throw badRequest("搜索词不能超过 100 字");
        Page<RewriteProject> result = projects.search(user, status,
                RewriteProject.Status.ARCHIVED, query, pageable);
        return result.map(this::summary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(User user, Long projectId) {
        return summary(owned(user, projectId));
    }

    @Transactional
    public Map<String, Object> updateProject(User user, Long projectId, String name,
                                              String description, Long currentChapterId) {
        RewriteProject project = owned(user, projectId);
        if (name != null) {
            if (name.isBlank() || name.length() > 200) throw badRequest("项目名称不能为空且不能超过 200 字");
            project.setName(name.strip());
        }
        if (description != null) {
            if (description.length() > 10_000) throw badRequest("项目说明不能超过 10000 字");
            project.setDescription(description.strip());
        }
        if (currentChapterId != null) {
            ownedChapter(project, currentChapterId);
            project.setCurrentChapterId(currentChapterId);
        }
        return summary(project);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> chapterList(User user, Long projectId) {
        return chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(owned(user, projectId))
                .stream().map(this::chapterSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> chapter(User user, Long projectId, Long chapterId) {
        return chapterDetail(ownedChapter(owned(user, projectId), chapterId));
    }

    @Transactional
    public Map<String, Object> addChapter(User user, Long projectId, String title,
                                           String volumeTitle) {
        RewriteProject project = editable(owned(user, projectId));
        if (title == null || title.isBlank() || title.length() > 500) {
            throw badRequest("章节标题不能为空且不能超过 500 字");
        }
        List<RewriteChapter> existing = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        RewriteChapter chapter = new RewriteChapter();
        chapter.setProject(project);
        chapter.setTitle(title.strip());
        chapter.setVolumeTitle(normalizeVolume(volumeTitle));
        chapter.setContent("");
        chapter.setSortIndex(existing.size());
        chapters.save(chapter);
        project.getRewriteVersion().setChapterCount(existing.size() + 1);
        return chapterDetail(chapter);
    }

    @Transactional
    public Map<String, Object> updateChapter(User user, Long projectId, Long chapterId,
                                              String title, RewriteChapter.Status status,
                                              String volumeTitle, Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        requireRevision(chapter, expectedRevision);
        if (title != null) {
            if (title.isBlank() || title.length() > 500) throw badRequest("章节标题不能为空且不能超过 500 字");
            chapter.setTitle(title.strip());
        }
        if (status != null) chapter.setStatus(status);
        if (volumeTitle != null) chapter.setVolumeTitle(normalizeVolume(volumeTitle));
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "METADATA");
        return chapterDetail(chapter);
    }

    @Transactional
    public Map<String, Object> saveContent(User user, Long projectId, Long chapterId,
                                            String content, Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        if (content == null || content.length() > MAX_CHAPTER_CHARS) {
            throw badRequest("章节正文不能为空值且不能超过 2500 万字符");
        }
        if (Objects.equals(chapter.getContent(), content)
                && (Objects.equals(chapter.getRevision(), expectedRevision)
                    || Objects.equals(chapter.getRevision(), expectedRevision == null
                            ? null : expectedRevision + 1))) {
            return chapterDetail(chapter);
        }
        requireRevision(chapter, expectedRevision);
        chapter.setContent(content.replace("\r\n", "\n").replace('\r', '\n'));
        chapter.setWordCount(wordCount(chapter.getContent()));
        if (chapter.getStatus() == RewriteChapter.Status.COMPLETED
                || chapter.getStatus() == RewriteChapter.Status.NOT_STARTED) {
            chapter.setStatus(RewriteChapter.Status.WRITING);
        }
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "CONTENT");
        project.setCurrentChapterId(chapter.getId());
        return chapterDetail(chapter);
    }

    @Transactional
    public Map<String, Object> restoreSource(User user, Long projectId, Long chapterId,
                                              Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        requireRevision(chapter, expectedRevision);
        if (chapter.getSourceContent() == null) throw badRequest("新增章节没有对应原文");
        chapter.setContent(chapter.getSourceContent());
        chapter.setWordCount(wordCount(chapter.getContent()));
        chapter.setStatus(RewriteChapter.Status.WRITING);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "RESTORE_SOURCE");
        return chapterDetail(chapter);
    }

    @Transactional
    public void deleteChapter(User user, Long projectId, Long chapterId) {
        RewriteProject project = editable(owned(user, projectId));
        if (chapters.countByProjectAndDeletedFalse(project) <= 1) {
            throw badRequest("不能删除最后一个章节");
        }
        RewriteChapter chapter = ownedChapter(project, chapterId);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "BEFORE_DELETE");
        chapter.setDeleted(true);
        List<RewriteChapter> remaining = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project)
                .stream().filter(item -> !Objects.equals(item.getId(), chapterId)).toList();
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setSortIndex(index);
        }
        project.getRewriteVersion().setChapterCount(remaining.size());
        if (Objects.equals(project.getCurrentChapterId(), chapterId)) {
            project.setCurrentChapterId(remaining.get(0).getId());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deletedChapters(User user, Long projectId) {
        return chapters.findByProjectAndDeletedTrueOrderBySortIndexAsc(owned(user, projectId))
                .stream().map(this::chapterSummary).toList();
    }

    @Transactional
    public Map<String, Object> restoreDeletedChapter(User user, Long projectId, Long chapterId) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = chapters.findById(chapterId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "已删除章节不存在"));
        if (!Objects.equals(chapter.getProject().getId(), projectId)
                || !Boolean.TRUE.equals(chapter.getDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "已删除章节不存在");
        }
        List<RewriteChapter> active = new ArrayList<>(
                chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project));
        int position = Math.min(chapter.getSortIndex(), active.size());
        active.add(position, chapter);
        for (int index = 0; index < active.size(); index++) {
            active.get(index).setSortIndex(index);
        }
        chapter.setDeleted(false);
        project.getRewriteVersion().setChapterCount(active.size());
        return chapterDetail(chapter);
    }

    @Transactional
    public void deleteProjectAndVersion(User user, Long projectId) {
        RewriteProject project = owned(user, projectId);
        BookVersion rewrite = project.getRewriteVersion();
        for (RewriteChapter chapter : chapters.findByProject(project)) {
            revisions.deleteByChapter(chapter);
            chapters.delete(chapter);
        }
        projects.delete(project);
        projects.flush();
        versionService.deleteVersion(project.getBook(), rewrite.getId());
    }

    @Transactional
    public void reorder(User user, Long projectId, List<Long> ids) {
        RewriteProject project = editable(owned(user, projectId));
        List<RewriteChapter> existing = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        if (ids == null || ids.size() != existing.size()
                || new HashSet<>(ids).size() != ids.size()
                || !new HashSet<>(ids).equals(existing.stream()
                        .map(RewriteChapter::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw badRequest("章节排序列表与当前项目不一致");
        }
        Map<Long, Integer> positions = new java.util.HashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            positions.put(ids.get(index), index);
        }
        for (RewriteChapter chapter : existing) {
            chapter.setSortIndex(positions.get(chapter.getId()));
        }
    }

    @Transactional
    public Map<String, Object> splitChapter(User user, Long projectId, Long chapterId,
                                             int position, String newTitle,
                                             Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        requireRevision(chapter, expectedRevision);
        if (newTitle == null || newTitle.isBlank() || newTitle.length() > 500) {
            throw badRequest("新章节标题不能为空且不能超过 500 字");
        }
        if (position <= 0 || position >= chapter.getContent().length()) {
            throw badRequest("拆分位置必须位于章节正文内部");
        }
        String before = chapter.getContent().substring(0, position);
        String after = chapter.getContent().substring(position);
        if (before.isBlank() || after.isBlank()) throw badRequest("不能拆分出空章节");
        List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        int index = active.indexOf(chapter);
        for (int next = index + 1; next < active.size(); next++) {
            active.get(next).setSortIndex(next + 1);
        }
        chapter.setContent(before);
        chapter.setWordCount(wordCount(before));
        chapter.setStatus(RewriteChapter.Status.WRITING);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "SPLIT");

        RewriteChapter created = new RewriteChapter();
        created.setProject(project);
        created.setSortIndex(index + 1);
        created.setTitle(newTitle.strip());
        created.setVolumeTitle(chapter.getVolumeTitle());
        created.setContent(after);
        created.setWordCount(wordCount(after));
        created.setSourceKey(chapter.getSourceKey());
        created.setSourceTitle(chapter.getSourceTitle());
        created.setSourceContent(chapter.getSourceContent());
        created.setStatus(RewriteChapter.Status.WRITING);
        chapters.save(created);
        project.getRewriteVersion().setChapterCount(active.size() + 1);
        return chapterDetail(created);
    }

    @Transactional
    public Map<String, Object> mergeWithNext(User user, Long projectId, Long chapterId,
                                              String title, Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter first = ownedChapter(project, chapterId);
        requireRevision(first, expectedRevision);
        List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        int index = active.indexOf(first);
        if (index < 0 || index + 1 >= active.size()) throw badRequest("没有可合并的下一章");
        RewriteChapter second = active.get(index + 1);
        if (!Objects.equals(first.getVolumeTitle(), second.getVolumeTitle())) {
            throw badRequest("跨卷合并前请先调整章节所属卷");
        }
        String merged = first.getContent() + "\n\n" + second.getContent();
        if (merged.length() > MAX_CHAPTER_CHARS) throw badRequest("合并后章节内容超出长度限制");
        String mergedTitle = title == null ? first.getTitle() : title.strip();
        if (mergedTitle.isBlank() || mergedTitle.length() > 500) {
            throw badRequest("合并后的标题不能为空且不能超过 500 字");
        }
        first.setTitle(mergedTitle);
        first.setContent(merged);
        first.setWordCount(wordCount(merged));
        first.setStatus(RewriteChapter.Status.WRITING);
        if (second.getSourceContent() != null) {
            String secondSource = second.getSourceTitle() + "\n\n" + second.getSourceContent();
            first.setSourceContent(first.getSourceContent() == null
                    ? secondSource : first.getSourceContent() + "\n\n" + secondSource);
            first.setSourceTitle(truncate(Objects.toString(first.getSourceTitle(), "")
                    + " / " + second.getSourceTitle(), 500));
            first.setSourceKey(truncate(Objects.toString(first.getSourceKey(), "")
                    + "," + second.getSourceKey(), 500));
        }
        first.setRevision(first.getRevision() + 1);
        saveRevision(first, "MERGE");
        second.setRevision(second.getRevision() + 1);
        saveRevision(second, "BEFORE_MERGE");
        second.setDeleted(true);
        for (int next = index + 2; next < active.size(); next++) {
            active.get(next).setSortIndex(next - 1);
        }
        project.getRewriteVersion().setChapterCount(active.size() - 1);
        return chapterDetail(first);
    }

    @Transactional
    public Map<String, Object> changeStatus(User user, Long projectId,
                                             RewriteProject.Status target,
                                             boolean force) {
        RewriteProject project = owned(user, projectId);
        RewriteProject.Status current = project.getStatus();
        boolean allowed = switch (target) {
            case PAUSED -> current == RewriteProject.Status.ACTIVE
                    || current == RewriteProject.Status.ARCHIVED;
            case ACTIVE -> current == RewriteProject.Status.PAUSED;
            case ARCHIVED -> current == RewriteProject.Status.ACTIVE
                    || current == RewriteProject.Status.PAUSED;
            case COMPLETED -> current == RewriteProject.Status.ACTIVE;
        };
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前项目状态不允许此操作");
        if (target == RewriteProject.Status.COMPLETED) {
            List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
            if (active.isEmpty() || active.stream().anyMatch(chapter -> chapter.getContent().isBlank())) {
                throw badRequest("存在空章节，无法完成重写");
            }
            long incomplete = active.stream()
                    .filter(chapter -> chapter.getStatus() != RewriteChapter.Status.COMPLETED)
                    .count();
            if (incomplete > 0 && !force) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "仍有 " + incomplete + " 章未完成，请确认后继续");
            }
            for (int index = 0; index < active.size(); index++) {
                RewriteChapter chapter = active.get(index);
                LibraryChapter publication = new LibraryChapter();
                publication.setBookVersion(project.getRewriteVersion());
                publication.setChapterKey("rewrite:" + project.getId() + ":" + chapter.getId());
                publication.setChapterIndex(index);
                publication.setTitle(chapter.getTitle());
                publication.setContent(chapter.getContent());
                publication.setContentHash(sha256(chapter.getContent()));
                publication.setWordCount(chapter.getWordCount());
                libraryChapters.save(publication);
            }
            project.setIncompleteCount((int) incomplete);
            project.setCompletedAt(LocalDateTime.now());
            project.getRewriteVersion().setFileSize(active.stream()
                    .mapToLong(chapter -> chapter.getContent().getBytes(StandardCharsets.UTF_8).length)
                    .sum());
        }
        project.setStatus(target);
        return summary(project);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> revisions(User user, Long projectId, Long chapterId) {
        RewriteChapter chapter = ownedChapter(owned(user, projectId), chapterId);
        return this.revisions.findByChapterOrderByRevisionNumberDesc(chapter).stream()
                .map(revision -> Map.<String, Object>of(
                        "revision", revision.getRevisionNumber(),
                        "reason", revision.getReason(),
                        "createdAt", revision.getCreatedAt() == null ? "" : revision.getCreatedAt().toString()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> revision(User user, Long projectId, Long chapterId,
                                         Long revisionNumber) {
        RewriteChapter chapter = ownedChapter(owned(user, projectId), chapterId);
        RewriteRevision saved = revisions.findByChapterAndRevisionNumber(chapter, revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "历史修订不存在"));
        return Map.of("revision", saved.getRevisionNumber(),
                "reason", saved.getReason(), "content", saved.getContent());
    }

    @Transactional
    public Map<String, Object> restoreRevision(User user, Long projectId, Long chapterId,
                                                Long revisionNumber, Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        requireRevision(chapter, expectedRevision);
        RewriteRevision saved = revisions.findByChapterAndRevisionNumber(chapter, revisionNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "历史修订不存在"));
        chapter.setContent(saved.getContent());
        chapter.setWordCount(wordCount(chapter.getContent()));
        chapter.setStatus(RewriteChapter.Status.WRITING);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "RESTORE_REVISION");
        return chapterDetail(chapter);
    }

    @Transactional(readOnly = true)
    public RewriteProject projectForVersion(BookVersion version) {
        return projects.findByRewriteVersion(version).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RewriteChapter> readableChapters(RewriteProject project) {
        return chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
    }

    private List<ChapterData> parse(BookVersion version) {
        String format = Objects.toString(version.getFormat(), "").toLowerCase();
        if ("structured".equals(format)) {
            RewriteProject draft = projects.findByRewriteVersion(version).orElse(null);
            if (draft != null && draft.getStatus() != RewriteProject.Status.COMPLETED) {
                return chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(draft).stream()
                        .map(chapter -> new ChapterData(chapter.getTitle(), chapter.getContent(),
                                "rewrite:" + chapter.getId())).toList();
            }
            return libraryChapters.findByBookVersionOrderByChapterIndexAsc(version).stream()
                    .map(chapter -> new ChapterData(chapter.getTitle(), chapter.getContent(),
                            chapter.getChapterKey())).toList();
        }
        if (!"txt".equals(format) && !"epub".equals(format)) {
            throw badRequest("仅支持结构化章节、TXT 和 EPUB 作为重写来源");
        }
        try {
            Path path = Path.of(version.getFilePath());
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_SOURCE_BYTES) {
                throw badRequest("源文件不存在或超过 20MB 预览上限");
            }
            String text;
            List<ConversionChapterDTO> boundaries;
            if ("txt".equals(format)) {
                text = txtParser.readFileWithEncoding(path);
                List<TxtParserService.ChapterInfo> detected = objectMapper.readValue(
                        txtParser.parseChapters(path), new TypeReference<>() { });
                boundaries = new ArrayList<>();
                for (TxtParserService.ChapterInfo item : detected) {
                    boundaries.add(ConversionChapterDTO.builder()
                            .title(item.getTitle()).startIndex(item.getStartIndex())
                            .endIndex(item.getEndIndex()).build());
                }
            } else {
                EpubTextExtractor.ExtractedBook extracted = epubExtractor.extract(path);
                text = extracted.text();
                boundaries = extracted.chapters();
            }
            List<ChapterData> result = new ArrayList<>();
            if (!boundaries.isEmpty() && boundaries.get(0).getStartIndex() > 0) {
                String preface = text.substring(0, boundaries.get(0).getStartIndex()).strip();
                if (!preface.isEmpty()) result.add(new ChapterData("前言", preface, "preface"));
            }
            for (int index = 0; index < boundaries.size(); index++) {
                ConversionChapterDTO boundary = boundaries.get(index);
                int start = Math.max(0, Math.min(text.length(), boundary.getStartIndex()));
                int end = Math.max(start, Math.min(text.length(), boundary.getEndIndex()));
                String title = Objects.toString(boundary.getTitle(), "第 " + (index + 1) + " 章");
                String content = text.substring(start, end).strip();
                if (content.startsWith(title)) content = content.substring(title.length()).strip();
                result.add(new ChapterData(title, content, format + ":" + index));
            }
            if (result.isEmpty() && !text.isBlank()) {
                result.add(new ChapterData("全文", text.strip(), format + ":0"));
            }
            return result;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "源版本解析失败：" + exception.getMessage(), exception);
        }
    }

    private List<FinalChapter> finalizeChapters(List<ChapterData> originals,
                                                List<ChapterOverride> overrides,
                                                boolean singleChapter) {
        if (overrides != null && overrides.size() != originals.size()) {
            throw badRequest("章节预览结果已变化，请重新预览");
        }
        if (singleChapter) {
            StringBuilder content = new StringBuilder();
            for (ChapterData original : originals) {
                if (!content.isEmpty()) content.append("\n\n");
                content.append(original.title()).append("\n\n").append(original.content());
            }
            return List.of(new FinalChapter("全文", content.toString(), "single",
                    "全文", content.toString()));
        }
        List<FinalChapter> result = new ArrayList<>();
        for (int index = 0; index < originals.size(); index++) {
            ChapterData original = originals.get(index);
            ChapterOverride override = overrides == null ? null : overrides.get(index);
            String title = override == null ? original.title() : override.title();
            if (title == null || title.isBlank() || title.length() > 500) {
                throw badRequest("章节标题不能为空且不能超过 500 字");
            }
            if (override != null && override.mergeWithPrevious() && !result.isEmpty()) {
                FinalChapter previous = result.remove(result.size() - 1);
                String continued = "\n\n" + original.title() + "\n\n" + original.content();
                result.add(new FinalChapter(previous.title(),
                        previous.content() + continued,
                        previous.sourceKey(),
                        truncate(previous.sourceTitle() + " / " + original.title(), 500),
                        previous.sourceContent() + continued));
            } else {
                result.add(new FinalChapter(title.strip(), original.content(),
                        original.sourceKey(), original.title(), original.content()));
            }
        }
        return result;
    }

    private RewriteProject owned(User user, Long id) {
        RewriteProject project = projects.findByIdAndUser(id, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "重写项目不存在"));
        if (project.getBook().getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "源书籍已移入回收站");
        }
        return project;
    }

    private RewriteProject editable(RewriteProject project) {
        if (project.getStatus() != RewriteProject.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目当前不可编辑");
        }
        return project;
    }

    private RewriteChapter ownedChapter(RewriteProject project, Long chapterId) {
        return chapters.findByIdAndProjectAndDeletedFalse(chapterId, project).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "工作章节不存在"));
    }

    private void requireRevision(RewriteChapter chapter, Long expected) {
        if (expected == null || !Objects.equals(chapter.getRevision(), expected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "章节已被其他窗口修改，请重新加载");
        }
    }

    private void saveRevision(RewriteChapter chapter, String reason) {
        RewriteRevision revision = new RewriteRevision();
        revision.setChapter(chapter);
        revision.setRevisionNumber(chapter.getRevision());
        revision.setContent(chapter.getContent());
        revision.setReason(reason);
        revisions.save(revision);
    }

    private Map<String, Object> summary(RewriteProject project) {
        List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        long finished = active.stream().filter(chapter ->
                chapter.getStatus() == RewriteChapter.Status.COMPLETED).count();
        return Map.ofEntries(
                Map.entry("id", project.getId()),
                Map.entry("bookId", project.getBook().getId()),
                Map.entry("bookTitle", project.getBook().getTitle()),
                Map.entry("sourceVersionId", project.getSourceVersion().getId()),
                Map.entry("rewriteVersionId", project.getRewriteVersion().getId()),
                Map.entry("name", project.getName()),
                Map.entry("versionName", project.getRewriteVersion().getDisplayName()),
                Map.entry("description", Objects.toString(project.getDescription(), "")),
                Map.entry("status", project.getStatus().name()),
                Map.entry("chapterCount", active.size()),
                Map.entry("completedCount", finished),
                Map.entry("totalWordCount", active.stream().mapToLong(
                        chapter -> chapter.getWordCount() == null ? 0 : chapter.getWordCount()).sum()),
                Map.entry("progress", active.isEmpty() ? 0 : (int) (finished * 100 / active.size())),
                Map.entry("currentChapterId", project.getCurrentChapterId() == null
                        ? 0L : project.getCurrentChapterId()),
                Map.entry("updatedAt", project.getUpdatedAt() == null
                        ? "" : project.getUpdatedAt().toString()));
    }

    private Map<String, Object> chapterSummary(RewriteChapter chapter) {
        return Map.ofEntries(
                Map.entry("id", chapter.getId()),
                Map.entry("title", chapter.getTitle()),
                Map.entry("sortIndex", chapter.getSortIndex()),
                Map.entry("status", chapter.getStatus().name()),
                Map.entry("volumeTitle", Objects.toString(chapter.getVolumeTitle(), "")),
                Map.entry("wordCount", chapter.getWordCount()),
                Map.entry("revision", chapter.getRevision()),
                Map.entry("hasSource", chapter.getSourceContent() != null));
    }

    private Map<String, Object> chapterDetail(RewriteChapter chapter) {
        Map<String, Object> detail = new java.util.LinkedHashMap<>(chapterSummary(chapter));
        detail.put("content", chapter.getContent());
        detail.put("sourceTitle", chapter.getSourceTitle());
        detail.put("sourceContent", chapter.getSourceContent());
        return detail;
    }

    private int wordCount(String content) {
        return (int) content.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    private String normalizeVolume(String volumeTitle) {
        if (volumeTitle == null || volumeTitle.isBlank()) return null;
        if (volumeTitle.length() > 200) throw badRequest("卷标题不能超过 200 字");
        return volumeTitle.strip();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算章节摘要", exception);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
