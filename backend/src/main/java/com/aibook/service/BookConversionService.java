package com.aibook.service;

import com.aibook.dto.*;
import com.aibook.model.entity.*;
import com.aibook.repository.*;
import com.aibook.service.conversion.BookConverter;
import com.aibook.service.repair.EncodingDetectService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookConversionService {
    private static final long MAX_TXT_SIZE = 500L * 1024 * 1024;
    private final BookConversionTaskRepository taskRepository;
    private final BookRepository bookRepository;
    private final BookVersionRepository versionRepository;
    private final BookVersionService bookVersionService;
    private final EncodingDetectService encodingDetectService;
    private final TxtParserService txtParserService;
    private final ObjectMapper objectMapper;
    private final List<BookConverter> converters;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final RandomBookCoverRepository randomBookCoverRepository;

    @Value("${conversion.path:./conversion-tasks}") private String conversionPath;
    @Value("${conversion.retention-days:7}") private int retentionDays;
    @Value("${upload.path:./uploads}") private String uploadPath;
    @Value("${app.cover.dir:covers}") private String coverDir;

    @Transactional
    public BookConversionTaskDTO createFromUpload(User user, MultipartFile file) {
        validateTxt(file);
        Path source = null;
        try {
            Path sourceDir = taskRoot().resolve("sources");
            Files.createDirectories(sourceDir);
            source = sourceDir.resolve(UUID.randomUUID() + ".txt");
            file.transferTo(source);
            String filename = safeFilename(file.getOriginalFilename(), "未命名.txt");
            BookConversionTask task = BookConversionTask.builder()
                    .user(user).sourceFilename(filename).sourceFormat("txt").targetFormat("epub")
                    .sourcePath(source.toString()).uploadedSource(true)
                    .status(BookConversionTask.Status.CREATED).stage("等待分析").progress(0)
                    .title(stripExtension(filename)).language("zh-CN")
                    .expiresAt(LocalDateTime.now().plusDays(retentionDays)).build();
            return toDTO(analyze(taskRepository.save(task)));
        } catch (ResponseStatusException exception) {
            if (source != null) deleteQuietly(source.toString());
            throw exception;
        }
        catch (Exception exception) {
            if (source != null) deleteQuietly(source.toString());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "TXT 上传分析失败", exception);
        }
    }

    @Transactional
    public BookConversionTaskDTO createFromBook(User user, Long bookId, Long versionId) {
        Book book = ownedBook(user, bookId);
        BookVersion version = bookVersionService.resolveVersion(book, versionId);
        if (!"txt".equalsIgnoreCase(version.getFormat())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第一期仅支持 TXT 转 EPUB");
        }
        BookConversionTask task = BookConversionTask.builder()
                .user(user).sourceBookId(book.getId()).sourceVersionId(version.getId())
                .sourceFilename(version.getDisplayName()).sourceFormat("txt").targetFormat("epub")
                .sourcePath(version.getFilePath()).uploadedSource(false)
                .status(BookConversionTask.Status.CREATED).stage("等待分析").progress(0)
                .title(book.getTitle()).author(book.getAuthor()).description(book.getDescription())
                .isbn(book.getIsbn()).publisher(book.getPublisher()).publishDate(book.getPublishDate())
                .language(defaultString(book.getLanguage(), "zh-CN"))
                .categoryName(book.getCategory() == null ? null : book.getCategory().getName())
                .tagsJson(json(book.getTags().stream().map(Tag::getName).sorted().toList()))
                .coverPath(resolveCoverPath(book.getCoverUrl()))
                .expiresAt(LocalDateTime.now().plusDays(retentionDays)).build();
        try {
            return toDTO(analyze(taskRepository.save(task)));
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "书库 TXT 分析失败", exception);
        }
    }

    private BookConversionTask analyze(BookConversionTask task) throws Exception {
        task.setStatus(BookConversionTask.Status.ANALYZING);
        task.setStage("正在解析编码与章节"); task.setProgress(10); taskRepository.save(task);
        Path source = Paths.get(task.getSourcePath());
        EncodingDetectResult detection = encodingDetectService.detectEncoding(source);
        String text = encodingDetectService.decodeWithEncoding(source, detection.getEncoding());
        List<ConversionChapterDTO> chapters = parseChapters(source);
        task.setEncoding(detection.getEncoding());
        task.setNewlineFormat(detectNewline(Files.readString(source, java.nio.charset.StandardCharsets.ISO_8859_1)));
        task.setCharacterCount((long) text.length());
        int duplicateCount = (int) chapters.stream().collect(java.util.stream.Collectors.groupingBy(
                chapter -> chapter.getTitle().trim(), java.util.stream.Collectors.counting()))
                .values().stream().filter(count -> count > 1).count();
        task.setAnomalyCount(value(detection.getAnomalyCount()) + duplicateCount);
        task.setChaptersJson(json(chapters));
        BookConversionUpdateRequest defaults = new BookConversionUpdateRequest();
        defaults.setOutputFilename(safeEpubFilename(task.getTitle() + ".epub"));
        defaults.setEpubVersion("3"); defaults.setFirstLineIndent("2em");
        defaults.setParagraphSpacing("small"); defaults.setLineHeight(1.6);
        defaults.setRemoveExtraBlankLines(true); defaults.setTrimLineEnd(true);
        defaults.setNormalizeWidth(false);
        task.setSettingsJson(json(defaults));
        task.setOutputFilename(defaults.getOutputFilename());
        task.setStatus(BookConversionTask.Status.READY); task.setStage("内容分析完成"); task.setProgress(25);
        return taskRepository.save(task);
    }

    private List<ConversionChapterDTO> parseChapters(Path source) throws Exception {
        List<Map<String, Object>> raw = objectMapper.readValue(
                txtParserService.parseChapters(source), new TypeReference<>() {});
        List<ConversionChapterDTO> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> chapter = raw.get(i);
            result.add(ConversionChapterDTO.builder().index(i)
                    .title(Objects.toString(chapter.get("title"), "第 " + (i + 1) + " 章"))
                    .startIndex(((Number) chapter.getOrDefault("startIndex", 0)).intValue())
                    .endIndex(((Number) chapter.getOrDefault("endIndex", 0)).intValue()).ignored(false).build());
        }
        return result;
    }

    @Transactional
    public BookConversionTaskDTO update(User user, Long id, BookConversionUpdateRequest request) {
        BookConversionTask task = ownedTask(user, id);
        if (task.getStatus() == BookConversionTask.Status.CONVERTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务正在转换，暂不能修改");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "书籍名称不能为空");
        }
        task.setTitle(request.getTitle().trim()); task.setAuthor(trim(request.getAuthor()));
        task.setDescription(trim(request.getDescription())); task.setIsbn(trim(request.getIsbn()));
        task.setPublisher(trim(request.getPublisher())); task.setPublishDate(trim(request.getPublishDate()));
        task.setLanguage(defaultString(trim(request.getLanguage()), "zh-CN"));
        task.setCategoryName(trim(request.getCategoryName())); task.setSeriesName(trim(request.getSeriesName()));
        task.setSeriesIndex(trim(request.getSeriesIndex())); task.setTagsJson(json(request.getTags()));
        if (request.getChapters() != null && !request.getChapters().isEmpty()) {
            task.setChaptersJson(json(request.getChapters()));
        }
        request.setOutputFilename(safeEpubFilename(defaultString(request.getOutputFilename(), task.getTitle() + ".epub")));
        task.setOutputFilename(request.getOutputFilename()); task.setSettingsJson(json(request));
        if (task.getStatus() == BookConversionTask.Status.SUCCESS || task.getStatus() == BookConversionTask.Status.FAILED) {
            task.setStatus(BookConversionTask.Status.READY); task.setStage("配置已更新，可重新转换"); task.setProgress(25);
        }
        return toDTO(taskRepository.save(task));
    }

    @Transactional
    public BookConversionTaskDTO reanalyze(User user, Long id, String chapterPattern) {
        BookConversionTask task = ownedTask(user, id);
        try {
            if (chapterPattern == null || chapterPattern.isBlank()) {
                List<ConversionChapterDTO> chapters = parseChapters(Paths.get(task.getSourcePath()));
                task.setChaptersJson(json(chapters));
                BookConversionUpdateRequest settings = settings(task);
                settings.setChapterPattern(null);
                task.setSettingsJson(json(settings));
                task.setStage("已恢复自动章节识别");
                return toDTO(taskRepository.save(task));
            }
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(chapterPattern);
            String text = encodingDetectService.decodeWithEncoding(
                    Paths.get(task.getSourcePath()), task.getEncoding());
            List<ConversionChapterDTO> chapters = new ArrayList<>();
            int offset = 0;
            for (String line : text.split("\\n", -1)) {
                String title = line.trim();
                if (!title.isEmpty() && pattern.matcher(title).find()) {
                    chapters.add(ConversionChapterDTO.builder().index(chapters.size())
                            .title(title).startIndex(offset).ignored(false).build());
                }
                offset += line.length() + 1;
            }
            if (chapters.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "该规则未识别到任何章节");
            }
            for (int index = 0; index < chapters.size(); index++) {
                chapters.get(index).setEndIndex(index + 1 < chapters.size()
                        ? chapters.get(index + 1).getStartIndex() : text.length());
            }
            task.setChaptersJson(json(chapters));
            BookConversionUpdateRequest settings = settings(task);
            settings.setChapterPattern(chapterPattern);
            task.setSettingsJson(json(settings));
            task.setStage("已按自定义规则重新识别章节");
            return toDTO(taskRepository.save(task));
        } catch (java.util.regex.PatternSyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "章节正则表达式无效", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "章节重新识别失败", exception);
        }
    }

    @Transactional
    public BookConversionTaskDTO uploadCover(User user, Long id, MultipartFile file) {
        BookConversionTask task = ownedTask(user, id);
        if (file == null || file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面不能为空且不能超过 10MB");
        }
        String contentType = Objects.toString(file.getContentType(), "");
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP 封面");
        };
        try {
            Path directory = taskRoot().resolve("covers"); Files.createDirectories(directory);
            Path cover = directory.resolve(task.getId() + "-" + UUID.randomUUID() + extension);
            file.transferTo(cover); deleteTaskCover(task); task.setCoverPath(cover.toString());
            return toDTO(taskRepository.save(task));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "封面保存失败", exception);
        }
    }

    @Transactional
    public BookConversionTaskDTO chooseLibraryCover(User user, Long id, Long coverId) {
        BookConversionTask task = ownedTask(user, id);
        RandomBookCover cover = findLibraryCover(user, coverId);
        Path path = Paths.get(uploadPath, coverDir, cover.getStoredFilename()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "封面文件不存在");
        deleteTaskCover(task);
        task.setCoverPath(path.toString());
        return toDTO(taskRepository.save(task));
    }

    @Transactional
    public BookConversionTaskDTO randomCover(User user, Long id) {
        BookConversionTask task = ownedTask(user, id);
        List<RandomBookCover> covers = randomBookCoverRepository
                .findAllByUserOrderByCreatedAtDesc(user).stream()
                .filter(cover -> Files.isRegularFile(libraryCoverPath(cover)))
                .toList();
        if (covers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面库为空，请先在设置中添加封面");
        }
        RandomBookCover selected = covers.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(covers.size()));
        deleteTaskCover(task);
        task.setCoverPath(libraryCoverPath(selected).toString());
        return toDTO(taskRepository.save(task));
    }

    @Transactional
    public BookConversionTaskDTO convert(User user, Long id) {
        BookConversionTask task = ownedTask(user, id);
        if (task.getStatus() == BookConversionTask.Status.CONVERTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务正在转换");
        }
        long started = System.currentTimeMillis();
        task.setStatus(BookConversionTask.Status.CONVERTING); task.setStage("正在生成 EPUB 内容"); task.setProgress(55);
        task.setErrorMessage(null); taskRepository.save(task);
        try {
            BookConverter converter = converters.stream().filter(item -> item.supports(task.getSourceFormat(), task.getTargetFormat()))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持该转换格式"));
            Path directory = taskRoot().resolve("results"); Files.createDirectories(directory);
            Path output = directory.resolve(task.getId() + "-" + UUID.randomUUID() + ".epub");
            converter.convert(task, output);
            validateEpub(output);
            deleteQuietly(task.getOutputPath());
            task.setOutputPath(output.toString()); task.setOutputSize(Files.size(output));
            task.setElapsedMillis(System.currentTimeMillis() - started);
            task.setStatus(BookConversionTask.Status.SUCCESS); task.setStage("EPUB 校验完成"); task.setProgress(100);
        } catch (Exception exception) {
            task.setElapsedMillis(System.currentTimeMillis() - started);
            task.setStatus(BookConversionTask.Status.FAILED); task.setStage("生成 EPUB 失败"); task.setProgress(55);
            task.setErrorMessage(exception.getMessage());
        }
        return toDTO(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<BookConversionTaskDTO> list(User user) { return taskRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toDTO).toList(); }
    @Transactional(readOnly = true)
    public BookConversionTaskDTO get(User user, Long id) { return toDTO(ownedTask(user, id)); }

    @Transactional(readOnly = true)
    public Path result(User user, Long id) {
        BookConversionTask task = successTask(user, id); Path path = Paths.get(task.getOutputPath());
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "转换结果已过期或不存在");
        return path;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(User user, Long id, int chapterIndex) {
        BookConversionTask task = successTask(user, id);
        try {
            List<ConversionChapterDTO> chapters = chapters(task).stream().filter(ch -> !Boolean.TRUE.equals(ch.getIgnored())).toList();
            if (chapters.isEmpty()) {
                chapters = List.of(ConversionChapterDTO.builder().index(0).title("全文")
                        .startIndex(0).endIndex(Integer.MAX_VALUE).build());
            }
            int selected = Math.max(0, Math.min(chapters.size() - 1, chapterIndex));
            ConversionChapterDTO chapter = chapters.get(selected);
            String text = encodingDetectService.decodeWithEncoding(Paths.get(task.getSourcePath()), task.getEncoding());
            int start = Math.max(0, Math.min(text.length(), chapter.getStartIndex()));
            int end = Math.max(start, Math.min(text.length(), chapter.getEndIndex()));
            return Map.of("title", task.getTitle(), "author", defaultString(task.getAuthor(), "未知作者"),
                    "chapterIndex", selected, "chapterTitle", chapter.getTitle(), "chapterCount", chapters.size(),
                    "content", text.substring(start, Math.min(end, start + 30000)));
        } catch (Exception exception) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "预览生成失败", exception); }
    }

    @Transactional
    public BookVersionDTO attach(User user, Long id, Long bookId) {
        BookConversionTask task = successTask(user, id);
        return bookVersionService.addGeneratedVersion(ownedBook(user, bookId), result(user, id), task.getOutputFilename());
    }

    @Transactional
    public BookDTO createBook(User user, Long id) {
        BookConversionTask task = successTask(user, id);
        Path target = Paths.get(uploadPath).resolve(UUID.randomUUID() + ".epub");
        try {
            Files.createDirectories(target.getParent()); Files.copy(result(user, id), target);
            String hash = hash(target);
            if (bookRepository.findByFileHash(hash).isPresent() || versionRepository.findByFileHash(hash).isPresent()) {
                Files.deleteIfExists(target); throw new ResponseStatusException(HttpStatus.CONFLICT, "该 EPUB 已存在于书库");
            }
            Book book = Book.builder().title(task.getTitle()).author(task.getAuthor()).description(task.getDescription())
                    .isbn(task.getIsbn()).publisher(task.getPublisher()).publishDate(task.getPublishDate())
                    .language(task.getLanguage()).format("epub").filePath(target.toString()).fileSize(Files.size(target))
                    .fileHash(hash).user(user).coverUrl(copyCoverToLibrary(task)).build();
            if (task.getCategoryName() != null && !task.getCategoryName().isBlank()) {
                Category category = categoryRepository.findByUser(user).stream()
                        .filter(item -> item.getName().equalsIgnoreCase(task.getCategoryName()))
                        .findFirst().orElseGet(() -> categoryRepository.save(Category.builder()
                                .name(task.getCategoryName()).user(user).sortOrder(0).enabled(true).build()));
                book.setCategory(category);
            }
            List<String> tagNames = tags(task);
            Set<Tag> tags = new LinkedHashSet<>();
            for (String name : tagNames) {
                Tag tag = tagRepository.findByNameIgnoreCaseAndUser(name, user);
                if (tag == null) tag = tagRepository.save(Tag.builder().name(name).color("#5f9e7d").user(user).build());
                tags.add(tag);
            }
            book.setTags(tags); book = bookRepository.save(book); bookVersionService.ensurePrimaryVersion(book, task.getOutputFilename());
            return BookDTO.builder().id(book.getId()).title(book.getTitle()).author(book.getAuthor()).format(book.getFormat()).coverUrl(book.getCoverUrl()).build();
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { try { Files.deleteIfExists(target); } catch (Exception ignored) { } throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "新建书籍失败", exception); }
    }

    @Transactional
    public void delete(User user, Long id) {
        BookConversionTask task = ownedTask(user, id); deleteQuietly(task.getOutputPath()); deleteTaskCover(task);
        if (Boolean.TRUE.equals(task.getUploadedSource())) deleteQuietly(task.getSourcePath());
        taskRepository.delete(task);
    }

    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupExpired() { taskRepository.findByExpiresAtBefore(LocalDateTime.now()).forEach(task -> { deleteQuietly(task.getOutputPath()); deleteTaskCover(task); if (Boolean.TRUE.equals(task.getUploadedSource())) deleteQuietly(task.getSourcePath()); taskRepository.delete(task); }); }

    private BookConversionTask ownedTask(User user, Long id) { return taskRepository.findByIdAndUser(id, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "转换任务不存在")); }
    private BookConversionTask successTask(User user, Long id) { BookConversionTask task = ownedTask(user, id); if (task.getStatus() != BookConversionTask.Status.SUCCESS) throw new ResponseStatusException(HttpStatus.CONFLICT, "转换任务尚未成功"); return task; }
    private Book ownedBook(User user, Long id) { return bookRepository.findByIdAndUserAndDeletedAtIsNull(id, user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍不存在")); }
    private RandomBookCover findLibraryCover(User user, Long id) {
        return randomBookCoverRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "封面不存在"));
    }
    private Path libraryCoverPath(RandomBookCover cover) {
        return Paths.get(uploadPath, coverDir, cover.getStoredFilename()).toAbsolutePath().normalize();
    }
    private Path taskRoot() { return Paths.get(conversionPath).toAbsolutePath().normalize(); }
    private void validateTxt(MultipartFile file) { if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 TXT 文件"); String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT); if (!name.endsWith(".txt")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第一期仅支持 TXT 文件"); if (file.getSize() > MAX_TXT_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TXT 文件不能超过 500MB"); }
    private void validateEpub(Path output) throws Exception { try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(output.toFile())) { if (zip.getEntry("mimetype") == null || zip.getEntry("META-INF/container.xml") == null || zip.getEntry("OEBPS/content.opf") == null) throw new IllegalStateException("EPUB 结构校验失败"); } }
    private List<ConversionChapterDTO> chapters(BookConversionTask task) { try { return objectMapper.readValue(task.getChaptersJson(), new TypeReference<>() {}); } catch (Exception e) { return List.of(); } }
    private List<String> tags(BookConversionTask task) { try { return task.getTagsJson() == null ? List.of() : objectMapper.readValue(task.getTagsJson(), new TypeReference<>() {}); } catch (Exception e) { return List.of(); } }
    private BookConversionUpdateRequest settings(BookConversionTask task) { try { return objectMapper.readValue(task.getSettingsJson(), BookConversionUpdateRequest.class); } catch (Exception e) { return new BookConversionUpdateRequest(); } }
    private BookConversionTaskDTO toDTO(BookConversionTask task) { long size = 0; try { size = Files.size(Paths.get(task.getSourcePath())); } catch (Exception ignored) { } return BookConversionTaskDTO.builder().id(task.getId()).sourceBookId(task.getSourceBookId()).sourceVersionId(task.getSourceVersionId()).sourceFilename(task.getSourceFilename()).sourceFormat(task.getSourceFormat()).targetFormat(task.getTargetFormat()).status(task.getStatus().name()).stage(task.getStage()).progress(task.getProgress()).errorMessage(task.getErrorMessage()).title(task.getTitle()).author(task.getAuthor()).description(task.getDescription()).isbn(task.getIsbn()).publisher(task.getPublisher()).publishDate(task.getPublishDate()).language(task.getLanguage()).categoryName(task.getCategoryName()).tags(tags(task)).seriesName(task.getSeriesName()).seriesIndex(task.getSeriesIndex()).coverUrl(task.getCoverPath() == null ? null : "/api/conversions/" + task.getId() + "/cover").encoding(task.getEncoding()).newlineFormat(task.getNewlineFormat()).sourceSize(size).characterCount(task.getCharacterCount()).anomalyCount(task.getAnomalyCount()).chapters(chapters(task)).settings(settings(task)).outputFilename(task.getOutputFilename()).outputSize(task.getOutputSize()).elapsedMillis(task.getElapsedMillis()).expiresAt(task.getExpiresAt()).createdAt(task.getCreatedAt()).updatedAt(task.getUpdatedAt()).build(); }
    public Path cover(User user, Long id) { BookConversionTask task = ownedTask(user, id); if (task.getCoverPath() == null || !Files.isRegularFile(Paths.get(task.getCoverPath()))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "封面不存在"); return Paths.get(task.getCoverPath()); }
    private String resolveCoverPath(String coverUrl) { if (coverUrl == null || coverUrl.isBlank() || coverUrl.startsWith("http")) return null; String normalized = coverUrl.startsWith("/") ? coverUrl.substring(1) : coverUrl; Path direct = Paths.get(uploadPath).resolve(normalized).toAbsolutePath().normalize(); return Files.isRegularFile(direct) ? direct.toString() : null; }
    private String copyCoverToLibrary(BookConversionTask task) throws Exception { if (task.getCoverPath() == null || !Files.isRegularFile(Paths.get(task.getCoverPath()))) return null; String ext = extension(task.getCoverPath()); Path directory = Paths.get(uploadPath, coverDir); Files.createDirectories(directory); String filename = "conversion-" + UUID.randomUUID() + "." + ext; Files.copy(Paths.get(task.getCoverPath()), directory.resolve(filename)); return coverDir + "/" + filename; }
    private String hash(Path path) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[8192]; int read; while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read); } return HexFormat.of().formatHex(digest.digest()); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (Exception e) { throw new IllegalArgumentException("配置序列化失败", e); } }
    private String safeFilename(String value, String fallback) { String name = defaultString(value, fallback).replace('\\', '/'); return name.substring(name.lastIndexOf('/') + 1); }
    private String safeEpubFilename(String value) { String name = safeFilename(value, "未命名.epub").replaceAll("[\\r\\n]", "").trim(); return name.toLowerCase(Locale.ROOT).endsWith(".epub") ? name : name + ".epub"; }
    private String stripExtension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name; }
    private String defaultString(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private int value(Integer value) { return value == null ? 0 : value; }
    private String detectNewline(String text) { if (text.contains("\r\n")) return "CRLF"; if (text.contains("\r")) return "CR"; return "LF"; }
    private String extension(String value) { int dot = value.lastIndexOf('.'); return dot < 0 ? "jpg" : value.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private void deleteQuietly(String value) { if (value == null) return; try { Files.deleteIfExists(Paths.get(value)); } catch (Exception ignored) { } }
    private void deleteTaskCover(BookConversionTask task) { if (task.getCoverPath() == null) return; Path path = Paths.get(task.getCoverPath()).toAbsolutePath().normalize(); if (path.startsWith(taskRoot().resolve("covers"))) deleteQuietly(path.toString()); }
}
