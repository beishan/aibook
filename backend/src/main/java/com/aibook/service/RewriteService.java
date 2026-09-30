package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteProject;
import com.aibook.model.entity.RewriteRevision;
import com.aibook.model.entity.RewriteSnapshot;
import com.aibook.model.entity.RewriteMemo;
import com.aibook.model.entity.User;
import com.aibook.repository.LibraryChapterRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.RewriteChapterRepository;
import com.aibook.repository.RewriteProjectRepository;
import com.aibook.repository.RewriteRevisionRepository;
import com.aibook.repository.RewriteSnapshotRepository;
import com.aibook.repository.RewriteMemoRepository;
import com.aibook.service.conversion.EpubTextExtractor;
import com.aibook.service.conversion.EpubPackageWriter;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Owns the editable draft. Completed publications are materialized as immutable chapters. */
@Service
@RequiredArgsConstructor
public class RewriteService {

    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_CHAPTER_CHARS = 25_000_000;
    private static final int AUTOMATIC_SNAPSHOT_LIMIT = 30;

    private final BookService bookService;
    private final BookVersionService versionService;
    private final BookVersionRepository versionRepository;
    private final LibraryChapterRepository libraryChapters;
    private final RewriteProjectRepository projects;
    private final RewriteChapterRepository chapters;
    private final RewriteRevisionRepository revisions;
    private final RewriteSnapshotRepository snapshots;
    private final RewriteMemoRepository memos;
    private final TxtParserService txtParser;
    private final EpubTextExtractor epubExtractor;
    private final EpubPackageWriter epubPackageWriter;
    private final ObjectMapper objectMapper;
    private final RewriteContentCodec contentCodec;

    private final Cache<String, PreviewData> previews = Caffeine.newBuilder()
            .maximumWeight(80 * 1024 * 1024)
            .weigher((String key, PreviewData value) -> Math.max(1,
                    value.chapters().stream().mapToInt(chapter ->
                            chapter.content().length() * 2).sum()))
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build();

    public record ChapterData(String title, String content, String sourceKey) { }
    public record ChapterOverride(String title, boolean mergeWithPrevious) { }

    public record SearchOptions(String query, String scope, Long chapterId,
                                String volumeTitle, boolean matchCase,
                                boolean wholeWord, boolean regex) { }

    public record MemoRequest(Long id, RewriteMemo.Type type, String title,
                              String content, Long chapterId, Integer anchorPosition,
                              RewriteMemo.State state, List<String> aliases) { }

    public record ExportFile(String filename, String contentType, byte[] body) { }

    public record BulkRequest(List<Long> chapterIds, String action, String value,
                              Map<Long, Long> revisions) { }

    private record SnapshotChapter(Long id, int sortIndex, String title,
                                   String volumeTitle, String content,
                                   int contentFormatVersion,
                                   String sourceKey, String sourceTitle,
                                   String sourceContent, String status,
                                   int wordCount) { }

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

    @Transactional(readOnly = true)
    public ExportFile export(User user, Long projectId, String format,
                             boolean includeMetadata, boolean includeChapterTitles,
                             String chapterTitleStyle, int chapterSpacing) {
        RewriteProject project = owned(user, projectId);
        String normalizedFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (!Set.of("txt", "md", "epub").contains(normalizedFormat)) {
            throw badRequest("当前仅支持导出 TXT、Markdown 和 EPUB");
        }
        if (!("ORIGINAL".equals(chapterTitleStyle) || "NUMBERED".equals(chapterTitleStyle))
                || chapterSpacing < 1 || chapterSpacing > 5) {
            throw badRequest("导出选项无效");
        }

        boolean markdown = "md".equals(normalizedFormat);
        List<RewriteChapter> active = chapters
                .findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        if ("epub".equals(normalizedFormat)) {
            return createEpubExport(project, active, includeChapterTitles,
                    chapterTitleStyle, chapterSpacing);
        }
        StringBuilder output = new StringBuilder();
        if (includeMetadata) {
            String title = Objects.toString(project.getBook().getTitle(), "");
            if (!title.isBlank()) {
                output.append(markdown ? "# " : "")
                        .append(markdown ? contentCodec.markdownTitle(title) : title).append('\n');
            }
            String author = Objects.toString(project.getBook().getAuthor(), "").trim();
            if (!author.isBlank()) {
                output.append(markdown ? "\n作者：" : "作者：")
                        .append(markdown ? contentCodec.markdownTitle(author) : author).append('\n');
            }
        }

        String separator = "\n".repeat(chapterSpacing + 1);
        String previousVolume = null;
        for (int index = 0; index < active.size(); index++) {
            RewriteChapter chapter = active.get(index);
            String volume = Objects.toString(chapter.getVolumeTitle(), "").trim();
            StringBuilder chapterOutput = new StringBuilder();
            if (!volume.isBlank() && !volume.equals(previousVolume)) {
                chapterOutput.append(markdown ? "## " : "")
                        .append(markdown ? contentCodec.markdownTitle(volume) : volume)
                        .append("\n\n");
            }
            if (includeChapterTitles) {
                String title = "NUMBERED".equals(chapterTitleStyle)
                        ? "第" + (index + 1) + "章 · " + chapter.getTitle()
                        : chapter.getTitle();
                if (markdown) {
                    chapterOutput.append(volume.isBlank() ? "## " : "### ");
                }
                chapterOutput.append(markdown ? contentCodec.markdownTitle(title) : title)
                        .append("\n\n");
            }
            int contentFormat = Objects.requireNonNullElse(
                    chapter.getContentFormatVersion(), 0);
            String body = contentFormat == 1
                    ? (markdown ? contentCodec.markdown(chapter.getContent())
                    : contentCodec.plainText(chapter.getContent()))
                    : (markdown
                    ? contentCodec.markdown(contentCodec.plainTextDocument(chapter.getContent()))
                    : chapter.getContent());
            chapterOutput.append(body.stripTrailing());
            if (!output.isEmpty()) output.append(separator);
            output.append(chapterOutput);
            previousVolume = volume;
        }

        String extension = markdown ? "md" : "txt";
        String fileTitle = Objects.toString(project.getRewriteVersion().getDisplayName(), "重写稿");
        String filename = fileTitle.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (filename.isBlank()) filename = "重写稿";
        return new ExportFile(filename + "." + extension,
                markdown ? "text/markdown;charset=UTF-8" : "text/plain;charset=UTF-8",
                output.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ExportFile createEpubExport(RewriteProject project, List<RewriteChapter> active,
                                         boolean includeChapterTitles, String titleStyle,
                                         int chapterSpacing) {
        if (active.isEmpty()) throw badRequest("没有可导出的章节");
        String bookTitle = Objects.toString(project.getBook().getTitle(), "未命名书籍");
        String author = Objects.toString(project.getBook().getAuthor(), "").trim();
        if (author.isBlank()) author = "未知作者";
        String language = Objects.toString(project.getBook().getLanguage(), "zh-CN");
        if (language.isBlank()) language = "zh-CN";
        List<EpubPackageWriter.Chapter> epubChapters = new ArrayList<>();
        for (int index = 0; index < active.size(); index++) {
            RewriteChapter chapter = active.get(index);
            String chapterTitle = "NUMBERED".equals(titleStyle)
                    ? "第" + (index + 1) + "章 · " + chapter.getTitle()
                    : chapter.getTitle();
            String volumeTitle = Objects.toString(chapter.getVolumeTitle(), "").trim();
            StringBuilder body = new StringBuilder();
            if (includeChapterTitles && !volumeTitle.isBlank()) {
                body.append("<h2>").append(escapeHtml(volumeTitle)).append("</h2>");
            }
            body.append(renderEpubChapterContent(chapter));
            String navigationTitle = volumeTitle.isBlank()
                    ? chapterTitle : volumeTitle + " · " + chapterTitle;
            epubChapters.add(new EpubPackageWriter.Chapter(
                    chapterTitle, navigationTitle, body.toString(), includeChapterTitles));
        }

        EpubPackageWriter.Metadata metadata = new EpubPackageWriter.Metadata(
                bookTitle,
                author,
                language,
                project.getBook().getDescription(),
                project.getBook().getPublisher(),
                project.getBook().getPublishDate(),
                project.getBook().getIsbn(),
                "urn:uuid:" + UUID.randomUUID());
        Path temporary = null;
        try {
            temporary = Files.createTempFile("aibook-rewrite-export-", ".epub");
            epubPackageWriter.write(temporary, metadata, epubChapters,
                    epubStylesheet(chapterSpacing), null);
            byte[] epub = Files.readAllBytes(temporary);
            if (epub.length > 100L * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "生成的 EPUB 超过 100 MB 限制");
            }
            return new ExportFile(safeExportFilename(project, "epub"),
                    "application/epub+zip", epub);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "EPUB 导出失败", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // A failed cleanup does not invalidate a completed export.
                }
            }
        }
    }

    private String renderEpubChapterContent(RewriteChapter chapter) {
        int formatVersion = Objects.requireNonNullElse(chapter.getContentFormatVersion(), 0);
        String document = formatVersion == 1 ? chapter.getContent()
                : contentCodec.plainTextDocument(chapter.getContent());
        return contentCodec.renderedBlocks(document).stream()
                .map(block -> block.get("html").replace("<hr>", "<hr />")
                        .replace("<br>", "<br />"))
                .collect(java.util.stream.Collectors.joining());
    }

    private String epubStylesheet(int chapterSpacing) {
        double paragraphSpacing = 0.35 * chapterSpacing;
        return "body{font-family:serif;line-height:1.8;padding:1em;}"
                + "h1{text-align:center;margin:1.5em 0;}h2{margin:2em 0 1em;}"
                + "p+p{margin-top:" + paragraphSpacing + "em;}"
                + "blockquote{margin:1em 0;padding-left:1em;border-left:2px solid #999;}"
                + "ul,ol{padding-left:1.7em;}hr{width:30%;margin:1.5em auto;}"
                + "a{color:inherit;text-decoration:underline;}";
    }

    private String safeExportFilename(RewriteProject project, String extension) {
        String fileTitle = Objects.toString(project.getRewriteVersion().getDisplayName(), "重写稿");
        String filename = fileTitle.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return (filename.isBlank() ? "重写稿" : filename) + "." + extension;
    }

    private String escapeHtml(String value) {
        return Objects.toString(value, "").replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Transactional
    public Map<String, Object> updateProject(User user, Long projectId, String name,
                                              String description, Long currentChapterId,
                                              Integer currentChapterPosition) {
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
            if (!Objects.equals(project.getCurrentChapterId(), currentChapterId)) {
                project.setCurrentChapterPosition(0);
            }
            project.setCurrentChapterId(currentChapterId);
        }
        if (currentChapterPosition != null) {
            if (currentChapterPosition < 0 || currentChapterPosition > MAX_CHAPTER_CHARS) {
                throw badRequest("最近阅读位置无效");
            }
            project.setCurrentChapterPosition(currentChapterPosition);
        }
        return summary(project);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> chapterList(User user, Long projectId) {
        RewriteProject project = owned(user, projectId);
        Map<Long, Long> pendingMemoCounts = memos
                .findByProjectAndTypeAndStateAndChapterIsNotNull(project,
                        RewriteMemo.Type.NOTE, RewriteMemo.State.TODO).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        memo -> memo.getChapter().getId(),
                        java.util.stream.Collectors.counting()));
        return chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project).stream()
                .map(chapter -> {
                    Map<String, Object> summary = new java.util.LinkedHashMap<>(chapterSummary(chapter));
                    summary.put("pendingMemoCount", pendingMemoCounts.getOrDefault(chapter.getId(), 0L));
                    return summary;
                }).toList();
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
                                            String content, Integer contentFormatVersion,
                                            Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        if (content == null || content.length() > MAX_CHAPTER_CHARS) {
            throw badRequest("章节正文不能为空值且不能超过 2500 万字符");
        }
        int formatVersion = contentFormatVersion == null ? 0 : contentFormatVersion;
        if (formatVersion != 0 && formatVersion != 1) throw badRequest("正文格式版本不受支持");
        String normalized = formatVersion == 1
                ? normalizeRichDocument(content)
                : content.replace("\r\n", "\n").replace('\r', '\n');
        boolean migratingToRichText = formatVersion == 1
                && Objects.equals(Objects.requireNonNullElse(chapter.getContentFormatVersion(), 0), 0)
                && !chapter.getContent().isBlank();
        if (Objects.equals(chapter.getContent(), normalized)
                && Objects.equals(Objects.requireNonNullElse(chapter.getContentFormatVersion(), 0),
                        formatVersion)
                && (Objects.equals(chapter.getRevision(), expectedRevision)
                    || Objects.equals(chapter.getRevision(), expectedRevision == null
                            ? null : expectedRevision + 1))) {
            return chapterDetail(chapter);
        }
        requireRevision(chapter, expectedRevision);
        if (migratingToRichText) saveAutomaticSnapshot(project, "纯文本迁移前自动快照");
        chapter.setContent(normalized);
        chapter.setContentFormatVersion(formatVersion);
        chapter.setWordCount(wordCount(chapter.getContent(), formatVersion));
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
        chapter.setContentFormatVersion(0);
        chapter.setWordCount(wordCount(chapter.getContent()));
        chapter.setStatus(RewriteChapter.Status.WRITING);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "RESTORE_SOURCE");
        return chapterDetail(chapter);
    }

    @Transactional
    public void deleteChapter(User user, Long projectId, Long chapterId,
                              Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        requireRevision(chapter, expectedRevision);
        if (chapters.countByProjectAndDeletedFalse(project) <= 1) {
            throw badRequest("不能删除最后一个章节");
        }
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
            project.setCurrentChapterPosition(0);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deletedChapters(User user, Long projectId) {
        return chapters.findByProjectAndDeletedTrueOrderBySortIndexAsc(owned(user, projectId))
                .stream().map(this::chapterSummary).toList();
    }

    @Transactional
    public Map<String, Object> restoreDeletedChapter(User user, Long projectId, Long chapterId,
                                                      Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = chapters.findById(chapterId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "已删除章节不存在"));
        if (!Objects.equals(chapter.getProject().getId(), projectId)
                || !Boolean.TRUE.equals(chapter.getDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "已删除章节不存在");
        }
        requireRevision(chapter, expectedRevision);
        List<RewriteChapter> active = new ArrayList<>(
                chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project));
        int position = Math.min(chapter.getSortIndex(), active.size());
        active.add(position, chapter);
        for (int index = 0; index < active.size(); index++) {
            active.get(index).setSortIndex(index);
        }
        chapter.setDeleted(false);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "RESTORE_DELETED");
        project.getRewriteVersion().setChapterCount(active.size());
        return chapterDetail(chapter);
    }

    @Transactional
    public void deleteProjectAndVersion(User user, Long projectId) {
        RewriteProject project = owned(user, projectId);
        BookVersion rewrite = project.getRewriteVersion();
        memos.deleteByProject(project);
        snapshots.deleteAll(snapshots.findByProjectOrderBySnapshotNumberDesc(project));
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
    public Map<String, Object> bulkChapters(User user, Long projectId, BulkRequest request) {
        RewriteProject project = editable(owned(user, projectId));
        List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        List<Long> ids = request == null ? null : request.chapterIds();
        if (ids == null || ids.isEmpty() || ids.size() > 500
                || new HashSet<>(ids).size() != ids.size()) {
            throw badRequest("批量操作需要选择 1 至 500 个不重复章节");
        }
        Map<Long, RewriteChapter> byId = active.stream().collect(java.util.stream.Collectors.toMap(
                RewriteChapter::getId, chapter -> chapter));
        List<RewriteChapter> selected = ids.stream().map(byId::get).toList();
        if (selected.stream().anyMatch(Objects::isNull)) throw badRequest("选择中包含无效章节");
        String action = Objects.toString(request.action(), "").toUpperCase();
        if (!Set.of("STATUS", "VOLUME", "DELETE", "RENUMBER").contains(action)) {
            throw badRequest("批量操作类型无效");
        }
        if ("DELETE".equals(action) && active.size() - selected.size() < 1) {
            throw badRequest("不能删除最后一个有效章节");
        }
        for (RewriteChapter chapter : selected) {
            Long expected = request.revisions() == null ? null : request.revisions().get(chapter.getId());
            requireRevision(chapter, expected);
        }
        RewriteSnapshot recoveryPoint = saveAutomaticSnapshot(project, "批量操作前自动快照");
        if ("STATUS".equals(action)) {
            RewriteChapter.Status status;
            try {
                status = RewriteChapter.Status.valueOf(request.value());
            } catch (Exception exception) {
                throw badRequest("章节状态无效");
            }
            for (RewriteChapter chapter : selected) {
                chapter.setStatus(status);
                chapter.setRevision(chapter.getRevision() + 1);
                saveRevision(chapter, "BULK_STATUS");
            }
        } else if ("VOLUME".equals(action)) {
            String volumeTitle = normalizeVolume(request.value());
            for (RewriteChapter chapter : selected) {
                chapter.setVolumeTitle(volumeTitle);
                chapter.setRevision(chapter.getRevision() + 1);
                saveRevision(chapter, "BULK_VOLUME");
            }
        } else if ("RENUMBER".equals(action)) {
            RenumberOptions options = parseRenumberOptions(request.value());
            List<RewriteChapter> ordered = selected.stream()
                    .sorted(java.util.Comparator.comparingInt(RewriteChapter::getSortIndex))
                    .toList();
            List<String> skipped = new ArrayList<>();
            int number = options.start();
            for (RewriteChapter chapter : ordered) {
                String renamed = renumberTitle(chapter.getTitle(), number, options.style());
                if (renamed == null) {
                    skipped.add(chapter.getTitle());
                    continue;
                }
                if (!renamed.equals(chapter.getTitle())) {
                    chapter.setTitle(renamed);
                    chapter.setRevision(chapter.getRevision() + 1);
                    saveRevision(chapter, "BULK_RENUMBER");
                }
                number++;
            }
            return Map.of("changedCount", ordered.size() - skipped.size(),
                    "skippedTitles", skipped, "recoverySnapshotId", recoveryPoint.getId());
        } else {
            for (RewriteChapter chapter : selected) {
                chapter.setRevision(chapter.getRevision() + 1);
                saveRevision(chapter, "BEFORE_BULK_DELETE");
                chapter.setDeleted(true);
            }
            List<RewriteChapter> remaining = active.stream()
                    .filter(chapter -> !ids.contains(chapter.getId())).toList();
            for (int index = 0; index < remaining.size(); index++) {
                remaining.get(index).setSortIndex(index);
            }
            project.getRewriteVersion().setChapterCount(remaining.size());
            if (ids.contains(project.getCurrentChapterId())) {
                project.setCurrentChapterId(remaining.get(0).getId());
                project.setCurrentChapterPosition(0);
            }
        }
        return Map.of("changedCount", selected.size(), "recoverySnapshotId", recoveryPoint.getId());
    }

    @Transactional(readOnly = true)
    public String copyChapterContents(User user, Long projectId, List<Long> chapterIds) {
        RewriteProject project = owned(user, projectId);
        if (chapterIds == null || chapterIds.isEmpty() || chapterIds.size() > 500
                || new HashSet<>(chapterIds).size() != chapterIds.size()) {
            throw badRequest("复制正文需要选择 1 至 500 个不重复章节");
        }
        Map<Long, RewriteChapter> byId = chapters
                .findByProjectAndDeletedFalseOrderBySortIndexAsc(project).stream()
                .collect(java.util.stream.Collectors.toMap(RewriteChapter::getId, chapter -> chapter));
        List<RewriteChapter> selected = chapterIds.stream().map(byId::get).toList();
        if (selected.stream().anyMatch(Objects::isNull)) throw badRequest("选择中包含无效章节");
        StringBuilder text = new StringBuilder();
        for (RewriteChapter chapter : selected.stream()
                .sorted(java.util.Comparator.comparingInt(RewriteChapter::getSortIndex)).toList()) {
            if (!text.isEmpty()) text.append("\n\n");
            String body = Objects.equals(chapter.getContentFormatVersion(), 1)
                    ? contentCodec.plainText(chapter.getContent()) : chapter.getContent();
            text.append(chapter.getTitle()).append("\n\n").append(body);
            if (text.length() > 10_000_000) {
                throw badRequest("所选正文超过 1000 万字符，无法一次复制");
            }
        }
        return text.toString();
    }

    private record RenumberOptions(String style, int start) { }

    private RenumberOptions parseRenumberOptions(String value) {
        if (value == null || !value.matches("(ARABIC|CHINESE|ENGLISH|PADDED):[1-9][0-9]{0,3}")) {
            throw badRequest("自动编号格式无效");
        }
        String[] parts = value.split(":", 2);
        int start = Integer.parseInt(parts[1]);
        if (start > 9999) throw badRequest("起始编号不能超过 9999");
        return new RenumberOptions(parts[0], start);
    }

    private String renumberTitle(String title, int number, String style) {
        if (number > 9999) return null;
        Pattern prefixPattern = Pattern.compile(
                "^(?:第\\s*[0-9一二三四五六七八九十百千万零〇两]+\\s*[章节回集部篇卷]"
                        + "|Chapter\\s+\\d+|\\d{1,4}[.、:：)）\\s_-]+)\\s*",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = prefixPattern.matcher(title);
        if (!matcher.find()) return null;
        String remainder = title.substring(matcher.end()).stripLeading();
        String prefix = switch (style) {
            case "ARABIC" -> "第" + number + "章 ";
            case "CHINESE" -> "第" + chineseNumber(number) + "章 ";
            case "ENGLISH" -> "Chapter " + number + " ";
            case "PADDED" -> String.format(java.util.Locale.ROOT, "%03d. ", number);
            default -> throw badRequest("自动编号格式无效");
        };
        return prefix + remainder;
    }

    private String chineseNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        String[] units = {"", "十", "百", "千"};
        StringBuilder result = new StringBuilder();
        String text = Integer.toString(value);
        boolean zeroPending = false;
        for (int index = 0; index < text.length(); index++) {
            int digit = text.charAt(index) - '0';
            int unitIndex = text.length() - index - 1;
            if (digit == 0) {
                zeroPending = result.length() > 0;
                continue;
            }
            if (zeroPending) result.append('零');
            zeroPending = false;
            if (!(digit == 1 && unitIndex == 1 && result.isEmpty())) {
                result.append(digits[digit]);
            }
            result.append(units[unitIndex]);
        }
        return result.toString();
    }

    @Transactional
    public Map<String, Object> createSnapshot(User user, Long projectId, String name) {
        RewriteProject project = editable(owned(user, projectId));
        if (name == null || name.isBlank() || name.length() > 200) {
            throw badRequest("快照名称不能为空且不能超过 200 字");
        }
        return snapshotSummary(saveSnapshot(project, name.strip()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSnapshots(User user, Long projectId) {
        RewriteProject project = owned(user, projectId);
        return snapshots.findByProjectOrderBySnapshotNumberDesc(project).stream()
                .map(this::snapshotSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshotStorage(User user, Long projectId) {
        RewriteProject project = owned(user, projectId);
        return Map.of(
                "totalBytes", snapshots.totalStorageBytes(project.getId()),
                "automaticCount", snapshots.countByProjectAndAutomaticTrue(project),
                "automaticLimit", AUTOMATIC_SNAPSHOT_LIMIT,
                "namedCount", snapshots.countByProjectAndAutomaticFalse(project));
    }

    @Transactional
    public void deleteNamedSnapshot(User user, Long projectId, Long snapshotId) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteSnapshot snapshot = snapshots.findByIdAndProject(snapshotId, project)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "整书快照不存在"));
        if (snapshot.isAutomatic()) throw badRequest("自动恢复点由系统保留策略管理");
        snapshots.delete(snapshot);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewSnapshotRestore(User user, Long projectId, Long snapshotId) {
        RewriteProject project = owned(user, projectId);
        RewriteSnapshot snapshot = snapshots.findByIdAndProject(snapshotId, project)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "整书快照不存在"));
        try {
            List<SnapshotChapter> saved = objectMapper.readValue(snapshot.getSnapshotContent(),
                    new TypeReference<>() { });
            List<RewriteChapter> active = chapters
                    .findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
            Map<Long, RewriteChapter> currentById = active.stream().collect(
                    java.util.stream.Collectors.toMap(RewriteChapter::getId, chapter -> chapter));
            Set<Long> savedIds = new HashSet<>();
            List<String> addedTitles = new ArrayList<>();
            List<String> removedTitles = active.stream()
                    .filter(chapter -> saved.stream().noneMatch(item ->
                            Objects.equals(item.id(), chapter.getId())))
                    .map(RewriteChapter::getTitle).limit(10).toList();
            int moved = 0;
            int changed = 0;
            for (int index = 0; index < saved.size(); index++) {
                SnapshotChapter item = saved.get(index);
                savedIds.add(item.id());
                RewriteChapter current = currentById.get(item.id());
                if (current == null) {
                    if (addedTitles.size() < 10) addedTitles.add(item.title());
                    continue;
                }
                if (current.getSortIndex() != index) moved++;
                if (!Objects.equals(current.getTitle(), item.title())
                        || !Objects.equals(current.getVolumeTitle(), item.volumeTitle())
                        || !Objects.equals(current.getContent(), item.content())
                        || !Objects.equals(current.getStatus().name(), item.status())
                        || !Objects.equals(Objects.requireNonNullElse(
                                current.getContentFormatVersion(), 0), item.contentFormatVersion())) {
                    changed++;
                }
            }
            long removed = active.stream().filter(chapter -> !savedIds.contains(chapter.getId())).count();
            return Map.of("added", saved.stream().filter(item -> !currentById.containsKey(item.id())).count(),
                    "removed", removed, "moved", moved, "changed", changed,
                    "addedTitles", addedTitles, "removedTitles", removedTitles);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "整书快照预览失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMemos(User user, Long projectId, RewriteMemo.Type type) {
        RewriteProject project = owned(user, projectId);
        List<RewriteMemo> items = type == null
                ? memos.findByProjectOrderByUpdatedAtDesc(project)
                : memos.findByProjectAndTypeOrderByUpdatedAtDesc(project, type);
        return items.stream().map(this::memoSummary).toList();
    }

    @Transactional
    public Map<String, Object> saveMemo(User user, Long projectId, MemoRequest request) {
        RewriteProject project = editable(owned(user, projectId));
        if (request == null || request.type() == null || request.title() == null
                || request.title().isBlank() || request.title().length() > 200
                || request.content() == null || request.content().length() > 50_000) {
            throw badRequest("资料标题不能为空且不超过 200 字，正文不能超过 50000 字");
        }
        List<String> aliases = normalizeAliases(request.aliases());
        if (request.type() == RewriteMemo.Type.NOTE && !aliases.isEmpty()) {
            throw badRequest("章节备注不支持别名");
        }
        RewriteMemo memo;
        if (request.id() == null) {
            memo = new RewriteMemo();
            memo.setProject(project);
        } else {
            memo = memos.findByIdAndProject(request.id(), project).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "资料条目不存在"));
        }
        RewriteChapter chapter = request.chapterId() == null ? null
                : ownedChapter(project, request.chapterId());
        if (request.anchorPosition() != null && (request.anchorPosition() < 0
                || request.anchorPosition() > MAX_CHAPTER_CHARS)) {
            throw badRequest("正文锚点位置无效");
        }
        memo.setType(request.type());
        memo.setTitle(request.title().strip());
        memo.setContent(request.content());
        memo.setState(Objects.requireNonNullElse(request.state(), RewriteMemo.State.TODO));
        try {
            memo.setAliases(objectMapper.writeValueAsString(aliases));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "资料别名保存失败", exception);
        }
        memo.setChapter(chapter);
        memo.setAnchorPosition(request.anchorPosition());
        return memoSummary(memos.save(memo));
    }

    private List<String> normalizeAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) return List.of();
        if (aliases.size() > 20) throw badRequest("每条资料最多添加 20 个别名");
        List<String> normalized = aliases.stream().filter(Objects::nonNull)
                .map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
        if (normalized.size() > 20 || normalized.stream().anyMatch(value -> value.length() > 50)) {
            throw badRequest("资料别名不能为空且每个不超过 50 字");
        }
        return normalized;
    }

    @Transactional
    public void deleteMemo(User user, Long projectId, Long memoId) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteMemo memo = memos.findByIdAndProject(memoId, project).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "资料条目不存在"));
        memos.delete(memo);
    }

    @Transactional
    public Map<String, Object> restoreSnapshot(User user, Long projectId, Long snapshotId) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteSnapshot snapshot = snapshots.findByIdAndProject(snapshotId, project)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "整书快照不存在"));
        RewriteSnapshot automaticSnapshot = saveAutomaticSnapshot(project, "恢复前自动快照");
        try {
            List<SnapshotChapter> saved = objectMapper.readValue(snapshot.getSnapshotContent(),
                    new TypeReference<>() { });
            List<RewriteChapter> all = chapters.findByProject(project);
            Map<Long, RewriteChapter> byId = all.stream().collect(java.util.stream.Collectors.toMap(
                    RewriteChapter::getId, chapter -> chapter));
            Set<Long> activeIds = all.stream().filter(chapter -> !chapter.getDeleted())
                    .map(RewriteChapter::getId)
                    .collect(java.util.stream.Collectors.toSet());
            all.stream().filter(chapter -> activeIds.contains(chapter.getId())).forEach(chapter -> {
                chapter.setRevision(chapter.getRevision() + 1);
                saveRevision(chapter, "BEFORE_SNAPSHOT_RESTORE");
                chapter.setDeleted(true);
            });
            List<RewriteChapter> restored = new ArrayList<>();
            for (int index = 0; index < saved.size(); index++) {
                SnapshotChapter item = saved.get(index);
                RewriteChapter chapter = byId.get(item.id());
                if (chapter == null) {
                    chapter = new RewriteChapter();
                    chapter.setProject(project);
                } else if (!activeIds.contains(chapter.getId())) {
                    chapter.setRevision(chapter.getRevision() + 1);
                    saveRevision(chapter, "BEFORE_SNAPSHOT_RESTORE");
                }
                chapter.setDeleted(false);
                chapter.setSortIndex(index);
                chapter.setTitle(item.title());
                chapter.setVolumeTitle(item.volumeTitle());
                chapter.setContent(item.content());
                chapter.setContentFormatVersion(item.contentFormatVersion());
                chapter.setSourceKey(item.sourceKey());
                chapter.setSourceTitle(item.sourceTitle());
                chapter.setSourceContent(item.sourceContent());
                chapter.setStatus(RewriteChapter.Status.valueOf(item.status()));
                chapter.setWordCount(item.wordCount());
                chapter.setRevision(chapter.getRevision() + 1);
                chapters.save(chapter);
                saveRevision(chapter, "RESTORE_SNAPSHOT");
                restored.add(chapter);
            }
            project.getRewriteVersion().setChapterCount(restored.size());
            if (!restored.isEmpty()) {
                project.setCurrentChapterId(restored.get(0).getId());
                project.setCurrentChapterPosition(0);
            }
            return Map.of("restored", restored.size(), "autoSnapshotId", automaticSnapshot.getId());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "整书快照无法恢复", exception);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(User user, Long projectId, SearchOptions options,
        String replacement) {
        RewriteProject project = owned(user, projectId);
        if (replacement != null && replacement.length() > 10_000) {
            throw badRequest("替换内容不能超过 10000 字");
        }
        Pattern pattern = searchPattern(options);
        String replacementPattern = options.regex()
                ? Objects.toString(replacement, "")
                : Matcher.quoteReplacement(Objects.toString(replacement, ""));
        List<RewriteChapter> candidates = matchingScope(project, options);
        List<Map<String, Object>> results = new ArrayList<>();
        int totalMatches = 0;
        for (RewriteChapter chapter : candidates) {
            String searchable = Objects.equals(chapter.getContentFormatVersion(), 1)
                    ? contentCodec.plainText(chapter.getContent()) : chapter.getContent();
            Matcher matcher = pattern.matcher(searchable);
            int count = 0;
            List<String> excerpts = new ArrayList<>();
            List<String> replacementSamples = new ArrayList<>();
            while (matcher.find()) {
                count++;
                totalMatches++;
                if (excerpts.size() < 3) {
                    int start = Math.max(0, matcher.start() - 45);
                    int end = Math.min(searchable.length(), matcher.end() + 65);
                    excerpts.add(searchable.substring(start, end));
                    String match = matcher.group();
                    if (match.length() > 10_000) {
                        replacementSamples.add("匹配内容超过 10000 字，已省略替换样例");
                    } else {
                        try {
                            String replacedMatch = pattern.matcher(match)
                                    .replaceFirst(replacementPattern);
                            replacementSamples.add(searchable.substring(start, matcher.start())
                                    + replacedMatch + searchable.substring(matcher.end(), end));
                        } catch (IllegalArgumentException exception) {
                            throw badRequest("替换表达式无效：" + exception.getMessage());
                        }
                    }
                }
                if (totalMatches >= 1000) break;
            }
            if (count > 0) {
                results.add(Map.of("chapterId", chapter.getId(), "title", chapter.getTitle(),
                        "matchCount", count, "excerpts", excerpts,
                        "replacementSamples", replacementSamples));
            }
            if (totalMatches >= 1000) break;
        }
        return Map.of("totalMatches", totalMatches, "truncated", totalMatches >= 1000,
                "results", results);
    }

    @Transactional
    public Map<String, Object> replace(User user, Long projectId, SearchOptions options,
                                       String replacement, Map<Long, Long> expectedRevisions) {
        RewriteProject project = editable(owned(user, projectId));
        if (replacement == null || replacement.length() > 10_000) {
            throw badRequest("替换内容不能超过 10000 字");
        }
        Pattern pattern = searchPattern(options);
        String replacementPattern = options.regex()
                ? replacement : Matcher.quoteReplacement(replacement);
        List<RewriteChapter> candidates = matchingScope(project, options);
        RewriteSnapshot recoveryPoint = saveAutomaticSnapshot(project, "查找替换前自动快照");
        List<RewriteChapter> changed = new ArrayList<>();
        long totalMatches = 0;
        long totalCharacters = 0;
        for (RewriteChapter chapter : candidates) {
            String searchable = Objects.equals(chapter.getContentFormatVersion(), 1)
                    ? contentCodec.plainText(chapter.getContent()) : chapter.getContent();
            Matcher matcher = pattern.matcher(searchable);
            if (!matcher.find()) continue;
            Long expected = expectedRevisions == null ? null : expectedRevisions.get(chapter.getId());
            requireRevision(chapter, expected);
            String replaced;
            long chapterMatches = 0;
            try {
                Matcher replacing = pattern.matcher(chapter.getContent());
                if (Objects.equals(chapter.getContentFormatVersion(), 1)) {
                    Matcher textMatcher = pattern.matcher(searchable);
                    while (textMatcher.find()) chapterMatches++;
                    RewriteContentCodec.TextReplacement result = contentCodec.replaceText(
                            chapter.getContent(), pattern, replacementPattern);
                    if (result.matches() != chapterMatches) {
                        throw badRequest("此处查找结果跨越了富文本格式边界，请逐段替换");
                    }
                    replaced = result.document();
                } else {
                    while (replacing.find()) chapterMatches++;
                    replaced = pattern.matcher(chapter.getContent()).replaceAll(replacementPattern);
                }
                totalMatches += chapterMatches;
                if (totalMatches > 1000) {
                    throw badRequest("单次替换最多处理 1000 处，请缩小查找范围");
                }
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (IllegalArgumentException exception) {
                throw badRequest("替换表达式无效：" + exception.getMessage());
            }
            totalCharacters += replaced.length();
            if (replaced.length() > MAX_CHAPTER_CHARS || totalCharacters > 100_000_000) {
                throw badRequest("替换结果超过正文长度限制");
            }
            if (!Objects.equals(replaced, chapter.getContent())) {
                chapter.setRevision(chapter.getRevision() + 1);
                saveRevision(chapter, "FIND_REPLACE");
                chapter.setContent(replaced);
                chapter.setWordCount(wordCount(replaced, Objects.requireNonNullElse(
                        chapter.getContentFormatVersion(), 0)));
                if (chapter.getStatus() == RewriteChapter.Status.COMPLETED
                        || chapter.getStatus() == RewriteChapter.Status.NOT_STARTED) {
                    chapter.setStatus(RewriteChapter.Status.WRITING);
                }
                changed.add(chapter);
            }
        }
        return Map.of("changedChapters", changed.size(), "matches", totalMatches,
                "recoverySnapshotId", recoveryPoint.getId(),
                "chapters", changed.stream().map(this::chapterSummary).toList());
    }

    @Transactional
    public Map<String, Object> splitChapter(User user, Long projectId, Long chapterId,
                                             int position, String newTitle,
                                             String beforeContent, String afterContent,
                                             Long expectedRevision) {
        RewriteProject project = editable(owned(user, projectId));
        RewriteChapter chapter = ownedChapter(project, chapterId);
        requireRevision(chapter, expectedRevision);
        if (newTitle == null || newTitle.isBlank() || newTitle.length() > 500) {
            throw badRequest("新章节标题不能为空且不能超过 500 字");
        }
        boolean rich = Objects.equals(chapter.getContentFormatVersion(), 1);
        String before;
        String after;
        if (rich) {
            if (beforeContent == null || afterContent == null) {
                throw badRequest("富文本拆分缺少章节片段");
            }
            before = normalizeRichDocument(beforeContent);
            after = normalizeRichDocument(afterContent);
            String originalText = contentCodec.plainText(chapter.getContent());
            String beforeText = contentCodec.plainText(before);
            String afterText = contentCodec.plainText(after);
            int seamStart = beforeText.length();
            int seamEnd = originalText.length() - afterText.length();
            if (beforeText.isBlank() || afterText.isBlank() || seamEnd < seamStart
                    || !originalText.startsWith(beforeText) || !originalText.endsWith(afterText)
                    || !originalText.substring(seamStart, seamEnd).matches("\\n{0,4}")) {
                throw badRequest("拆分片段与当前正文不一致或拆分结果为空");
            }
        } else {
            if (position <= 0 || position >= chapter.getContent().length()) {
                throw badRequest("拆分位置必须位于章节正文内部");
            }
            before = chapter.getContent().substring(0, position);
            after = chapter.getContent().substring(position);
            if (before.isBlank() || after.isBlank()) throw badRequest("不能拆分出空章节");
        }
        RewriteSnapshot recoveryPoint = saveAutomaticSnapshot(project, "章节拆分前自动快照");
        List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        int index = active.indexOf(chapter);
        for (int next = index + 1; next < active.size(); next++) {
            active.get(next).setSortIndex(next + 1);
        }
        chapter.setContent(before);
        chapter.setContentFormatVersion(rich ? 1 : 0);
        chapter.setWordCount(wordCount(before, rich ? 1 : 0));
        chapter.setStatus(RewriteChapter.Status.WRITING);
        chapter.setRevision(chapter.getRevision() + 1);
        saveRevision(chapter, "SPLIT");

        RewriteChapter created = new RewriteChapter();
        created.setProject(project);
        created.setSortIndex(index + 1);
        created.setTitle(newTitle.strip());
        created.setVolumeTitle(chapter.getVolumeTitle());
        created.setContent(after);
        created.setContentFormatVersion(rich ? 1 : 0);
        created.setWordCount(wordCount(after, rich ? 1 : 0));
        created.setSourceKey(chapter.getSourceKey());
        created.setSourceTitle(chapter.getSourceTitle());
        created.setSourceContent(chapter.getSourceContent());
        created.setStatus(RewriteChapter.Status.WRITING);
        chapters.save(created);
        project.getRewriteVersion().setChapterCount(active.size() + 1);
        Map<String, Object> result = new java.util.LinkedHashMap<>(chapterDetail(created));
        result.put("recoverySnapshotId", recoveryPoint.getId());
        return result;
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
        int firstFormat = Objects.requireNonNullElse(first.getContentFormatVersion(), 0);
        int secondFormat = Objects.requireNonNullElse(second.getContentFormatVersion(), 0);
        int mergedFormat = firstFormat == 1 || secondFormat == 1 ? 1 : 0;
        String merged = mergedFormat == 1
                ? contentCodec.mergeDocuments(first.getContent(), firstFormat,
                        second.getContent(), secondFormat)
                : first.getContent() + "\n\n" + second.getContent();
        if (merged.length() > MAX_CHAPTER_CHARS) throw badRequest("合并后章节内容超出长度限制");
        String mergedTitle = title == null ? first.getTitle() : title.strip();
        if (mergedTitle.isBlank() || mergedTitle.length() > 500) {
            throw badRequest("合并后的标题不能为空且不能超过 500 字");
        }
        RewriteSnapshot recoveryPoint = saveAutomaticSnapshot(project, "章节合并前自动快照");
        first.setTitle(mergedTitle);
        first.setContent(merged);
        first.setContentFormatVersion(mergedFormat);
        first.setWordCount(wordCount(merged, mergedFormat));
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
        Map<String, Object> result = new java.util.LinkedHashMap<>(chapterDetail(first));
        result.put("recoverySnapshotId", recoveryPoint.getId());
        return result;
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
                "reason", saved.getReason(), "content", saved.getContent(),
                "contentFormatVersion", Objects.requireNonNullElse(
                        saved.getContentFormatVersion(), 0));
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
        chapter.setContentFormatVersion(Objects.requireNonNullElse(
                saved.getContentFormatVersion(), 0));
        chapter.setWordCount(wordCount(chapter.getContent(), chapter.getContentFormatVersion()));
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
        revision.setContentFormatVersion(Objects.requireNonNullElse(
                chapter.getContentFormatVersion(), 0));
        revision.setReason(reason);
        revisions.save(revision);
    }

    private RewriteSnapshot saveSnapshot(RewriteProject project, String name) {
        return saveSnapshot(project, name, false);
    }

    private RewriteSnapshot saveAutomaticSnapshot(RewriteProject project, String name) {
        return saveSnapshot(project, name, true);
    }

    private RewriteSnapshot saveSnapshot(RewriteProject project, String name, boolean automatic) {
        List<SnapshotChapter> contents = chapters
                .findByProjectAndDeletedFalseOrderBySortIndexAsc(project).stream()
                .map(chapter -> new SnapshotChapter(chapter.getId(), chapter.getSortIndex(),
                        chapter.getTitle(), chapter.getVolumeTitle(), chapter.getContent(),
                        Objects.requireNonNullElse(chapter.getContentFormatVersion(), 0),
                        chapter.getSourceKey(), chapter.getSourceTitle(), chapter.getSourceContent(),
                        chapter.getStatus().name(), chapter.getWordCount())).toList();
        try {
            RewriteSnapshot snapshot = new RewriteSnapshot();
            snapshot.setProject(project);
            long latestNumber = snapshots.findTopByProjectOrderBySnapshotNumberDesc(project)
                    .map(RewriteSnapshot::getSnapshotNumber).orElse(0L);
            snapshot.setSnapshotNumber(latestNumber + 1);
            snapshot.setName(name);
            snapshot.setSnapshotContent(objectMapper.writeValueAsString(contents));
            snapshot.setAutomatic(automatic);
            RewriteSnapshot saved = snapshots.save(snapshot);
            if (automatic) pruneAutomaticSnapshots(project);
            return saved;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "整书快照保存失败", exception);
        }
    }

    private Map<String, Object> snapshotSummary(RewriteSnapshot snapshot) {
        return Map.of("id", snapshot.getId(), "number", snapshot.getSnapshotNumber(),
                "name", snapshot.getName(), "automatic", snapshot.isAutomatic(),
                "createdAt", snapshot.getCreatedAt() == null
                        ? "" : snapshot.getCreatedAt().toString());
    }

    private void pruneAutomaticSnapshots(RewriteProject project) {
        List<RewriteSnapshot> automatic = snapshots
                .findByProjectAndAutomaticTrueOrderBySnapshotNumberDesc(project);
        if (automatic.size() > AUTOMATIC_SNAPSHOT_LIMIT) {
            snapshots.deleteAll(automatic.subList(AUTOMATIC_SNAPSHOT_LIMIT, automatic.size()));
        }
    }

    private Map<String, Object> memoSummary(RewriteMemo memo) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", memo.getId());
        result.put("type", memo.getType().name());
        result.put("title", memo.getTitle());
        result.put("content", memo.getContent());
        result.put("state", memo.getState().name());
        try {
            result.put("aliases", objectMapper.readValue(memo.getAliases(),
                    new TypeReference<List<String>>() { }));
        } catch (Exception exception) {
            result.put("aliases", List.of());
        }
        result.put("chapterId", memo.getChapter() == null ? null : memo.getChapter().getId());
        result.put("chapterTitle", memo.getChapter() == null ? "" : memo.getChapter().getTitle());
        result.put("anchorPosition", memo.getAnchorPosition());
        result.put("updatedAt", memo.getUpdatedAt() == null ? "" : memo.getUpdatedAt().toString());
        return result;
    }

    private List<RewriteChapter> matchingScope(RewriteProject project, SearchOptions options) {
        if (options == null) throw badRequest("缺少查找条件");
        List<RewriteChapter> active = chapters.findByProjectAndDeletedFalseOrderBySortIndexAsc(project);
        String scope = Objects.toString(options.scope(), "BOOK").toUpperCase();
        if ("BOOK".equals(scope)) return active;
        if ("CHAPTER".equals(scope)) {
            return active.stream().filter(chapter -> Objects.equals(
                    chapter.getId(), options.chapterId())).toList();
        }
        if ("VOLUME".equals(scope)) {
            if (options.volumeTitle() == null || options.volumeTitle().isBlank()) {
                throw badRequest("请先选择章节卷");
            }
            return active.stream().filter(chapter -> Objects.equals(
                    chapter.getVolumeTitle(), options.volumeTitle())).toList();
        }
        throw badRequest("查找范围无效");
    }

    private Pattern searchPattern(SearchOptions options) {
        if (options == null || options.query() == null || options.query().isEmpty()
                || options.query().length() > 100) {
            throw badRequest("查找内容不能为空且不能超过 100 字");
        }
        String expression = options.query();
        if (options.regex()) {
            if (expression.matches(".*\\([^)]*[+*?{][^)]*\\)[+*?{].*")
                    || expression.matches(".*\\([^)]*\\|[^)]*\\)[+*?{].*")
                    || expression.matches(".*\\\\[1-9].*")
                    || expression.contains("(?=") || expression.contains("(?!")
                    || expression.contains("(?<=") || expression.contains("(?<!")) {
                throw badRequest("正则表达式包含可能导致长时间运行的重复结构");
            }
        } else {
            expression = Pattern.quote(expression);
        }
        if (options.wholeWord()) {
            expression = "(?<![\\p{L}\\p{N}_])(?:" + expression
                    + ")(?![\\p{L}\\p{N}_])";
        }
        try {
            return Pattern.compile(expression, options.matchCase() ? 0 : Pattern.CASE_INSENSITIVE
                    | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException exception) {
            throw badRequest("正则表达式无效：" + exception.getDescription());
        }
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
                Map.entry("currentChapterPosition", Objects.requireNonNullElse(
                        project.getCurrentChapterPosition(), 0)),
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
                Map.entry("hasSource", chapter.getSourceContent() != null),
                Map.entry("contentFormatVersion", Objects.requireNonNullElse(
                        chapter.getContentFormatVersion(), 0)));
    }

    private Map<String, Object> chapterDetail(RewriteChapter chapter) {
        Map<String, Object> detail = new java.util.LinkedHashMap<>(chapterSummary(chapter));
        detail.put("content", chapter.getContent());
        detail.put("contentFormatVersion", Objects.requireNonNullElse(
                chapter.getContentFormatVersion(), 0));
        detail.put("sourceTitle", chapter.getSourceTitle());
        detail.put("sourceContent", chapter.getSourceContent());
        return detail;
    }

    private int wordCount(String content) {
        return (int) content.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    private int wordCount(String content, int formatVersion) {
        return wordCount(formatVersion == 1 ? contentCodec.plainText(content) : content);
    }

    private String normalizeRichDocument(String content) {
        return contentCodec.normalizeDocument(content);
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
