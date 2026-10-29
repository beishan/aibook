package com.aibook.audiobook;

import static com.aibook.audiobook.AudioStore.number;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.ScanDirectoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AudioImportService {
    private static final int MAX_FILES = 5000;
    private static final java.util.regex.Pattern NUMBER_PARTS = java.util.regex.Pattern.compile("[0-9]+|[^0-9]+");
    private static final TypeReference<List<AudioTypes.Candidate>> CANDIDATES = new TypeReference<>() {};
    private final AudioStore store;
    private final AudiobookService audio;
    private final AudioProbeService probe;
    private final ScanDirectoryRepository directories;
    private final BookRepository books;
    private final com.aibook.service.BookCoverService covers;
    @Qualifier("audioProbeExecutor")
    private final ExecutorService executor;
    @Value("${upload.path:./uploads}")
    private String uploadPath;

    public synchronized Map<String, Object> scan(User user, AudioTypes.ScanRequest request) throws IOException {
        var directory = directories.findByIdAndUser(request.directoryId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "扫描目录不存在"));
        if (!Boolean.TRUE.equals(directory.getEnabled())) throw new IllegalArgumentException("扫描目录已禁用");
        Path root = Path.of(directory.getPath()).toRealPath();
        Path path = root.resolve(request.relativePath() == null ? "" : request.relativePath()).normalize().toRealPath();
        if (!path.startsWith(root) || !Files.isDirectory(path)) throw new IllegalArgumentException("请选择扫描目录内的文件夹");
        var task = admit(user, request.directoryId());
        execute(task, user.getId(), root, path, null);
        return task(user, task);
    }

    public synchronized Map<String, Object> upload(User user, List<MultipartFile> files) throws IOException {
        if (files.isEmpty() || files.size() > MAX_FILES) throw new IllegalArgumentException("请选择 1—5000 个音频文件");
        String id = admit(user, null);
        Path root = Path.of(uploadPath).toAbsolutePath().normalize().resolve("audio").resolve(id);
        var paths = new ArrayList<Path>();
        try {
            Files.createDirectories(root);
            long total = 0;
            for (int index = 0; index < files.size(); index++) {
                var file = files.get(index);
                total += file.getSize();
                if (total > 2L * 1024 * 1024 * 1024) throw new IllegalArgumentException("单批上传不能超过 2GB");
                String name = file.getOriginalFilename() == null ? "track.mp3" : Path.of(file.getOriginalFilename()).getFileName().toString();
                if (!supported(name)) throw new IllegalArgumentException("仅接受 MP3、M4A 或 M4B");
                Path destination = root.resolve(String.format("%05d-", index) + name);
                Path temporary = Files.createTempFile(root, "upload-", ".part");
                try (var input = file.getInputStream()) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); }
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                paths.add(destination);
            }
            execute(id, user.getId(), root, root, paths);
            return task(user, id);
        } catch (Exception exception) {
            store.update("UPDATE audio_import_tasks SET state='FAILED',error=? WHERE id=?", "上传失败，请重新上传", id);
            // Only remove files owned by this incomplete upload, never scanned source files.
            if (Files.exists(root)) {
                try (var stream = Files.walk(root)) {
                    for (Path file : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                }
            }
            throw exception;
        }
    }

    private String admit(User user, Long directoryId) {
        long busy = number(store.one("SELECT COUNT(*) AS count FROM audio_import_tasks WHERE state='SCANNING'"), "count");
        long userBusy = number(store.one("SELECT COUNT(*) AS count FROM audio_import_tasks WHERE state='SCANNING' AND user_id=?", user.getId()), "count");
        if (busy >= 2 || userBusy > 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "已有音频探测任务，请稍后重试");
        String id = UUID.randomUUID().toString();
        store.update("INSERT INTO audio_import_tasks(id,user_id,directory_id,state) VALUES(?,?,?,'SCANNING')", id, user.getId(), directoryId);
        return id;
    }

    private void execute(String id, long userId, Path root, Path path, List<Path> uploads) {
        executor.submit(scanJob(id,userId,root,path,uploads));
    }

    private Runnable scanJob(String id, long userId, Path root, Path path, List<Path> uploads) {
        return () -> {
            var results = new ArrayList<AudioTypes.Candidate>();
            try {
                List<Path> paths;
                if (uploads != null) paths = uploads;
                else {
                    try (var stream = Files.walk(path, 20)) {
                        paths = stream.filter(Files::isRegularFile).filter(p -> supported(p.getFileName().toString()))
                                .limit(MAX_FILES + 1L).sorted((a,b) -> naturalCompare(a.toString(), b.toString())).toList();
                    }
                }
                if (paths.size() > MAX_FILES) throw new IOException("目录超过 5000 个音频文件，请分组扫描");
                int chapterCount = 0;
                for (Path file : paths) {
                    if (Thread.currentThread().isInterrupted() || cancelled(id, userId)) return;
                    var candidate = candidate(userId, root, file, uploads == null);
                    chapterCount += candidate.chapters().size();
                    if (chapterCount > 20000) throw new IOException("单批章节资料超过 20000 章，请分批导入");
                    results.add(candidate);
                    if (results.size() % 5 == 0) saveCandidates(id, results);
                }
                var groups = results.stream().collect(java.util.stream.Collectors.groupingBy(AudioTypes.Candidate::group));
                var numbered = groups.entrySet().stream().filter(entry -> entry.getValue().stream().allMatch(c -> c.trackNumber() != null))
                        .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
                results.sort((a,b) -> {
                    int groupOrder = naturalCompare(a.group(),b.group());
                    if (groupOrder != 0) return groupOrder;
                    if (numbered.contains(a.group())) {
                        int disc = Integer.compare(a.discNumber() == null ? 1 : a.discNumber(), b.discNumber() == null ? 1 : b.discNumber());
                        if (disc != 0) return disc;
                        int track = Integer.compare(a.trackNumber(),b.trackNumber());
                        if (track != 0) return track;
                    }
                    return naturalCompare(a.path(),b.path());
                });
                saveCandidates(id, results);
                store.update("UPDATE audio_import_tasks SET state='READY' WHERE id=? AND state='SCANNING'", id);
            } catch (Exception exception) {
                try { saveCandidates(id, results); }
                catch (RuntimeException ignored) { /* Keep the last bounded preview snapshot. */ }
                store.update("UPDATE audio_import_tasks SET state='FAILED',error=? WHERE id=? AND state='SCANNING'",
                        exception.getMessage() == null ? "音频探测失败" : exception.getMessage(), id);
            }
        };
    }

    /** Serial preview generation for all enabled audio roots at the user's existing scan time. */
    public synchronized void scheduled(User user, List<com.aibook.model.entity.ScanDirectory> roots) {
        if (roots.isEmpty()) return;
        String first = admit(user,roots.getFirst().getId());
        executor.submit(() -> {
            for (int index = 0; index < roots.size(); index++) {
                var directory = roots.get(index);
                String id = first;
                try {
                    if (index > 0) {
                        synchronized (this) { id = admit(user,directory.getId()); }
                    }
                    Path root = Path.of(directory.getPath()).toRealPath();
                    scanJob(id,user.getId(),root,root,null).run();
                } catch (Exception error) {
                    store.update("UPDATE audio_import_tasks SET state='FAILED',error=? WHERE id=? AND state='SCANNING'", "定时探测失败，请手动重试",id);
                }
            }
        });
    }

    private AudioTypes.Candidate candidate(long userId, Path root, Path source, boolean requireStable) {
        String path = source.toAbsolutePath().toString();
        String group = root.relativize(source.getParent()).toString();
        try {
            Path file = source.toRealPath();
            if (!file.startsWith(root)) throw new IOException("文件通过符号链接超出扫描目录");
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (requireStable && Instant.now().toEpochMilli() - modified < 10000) throw new IOException("文件可能正在复制，请稍后重试");
            var cached = store.rows("SELECT t.* FROM audio_tracks t JOIN audiobook_editions e ON e.id=t.edition_id "
                    + "JOIN books b ON b.id=e.book_id WHERE b.user_id=? AND t.file_path=? AND t.file_size=? AND t.modified_ms=? LIMIT 1",
                    userId, file.toString(), size, modified);
            if (!cached.isEmpty()) {
                var entry = cached.getFirst();
                return new AudioTypes.Candidate(file.toString(), root.toString(), group, entry.get("title").toString(),
                        number(entry,"durationMs"), size, modified, entry.get("fileHash").toString(),
                        entry.get("mimeType").toString(), entry.get("codec").toString(),
                        entry.get("discNumber") == null ? null : (int)number(entry,"discNumber"),
                        entry.get("trackNumber") == null ? null : (int)number(entry,"trackNumber"), audio.read(entry.get("extractedChapters").toString(), new TypeReference<List<AudioTypes.ProbeChapter>>() {}), null);
            }
            var result = probe.probe(file);
            String hash = AudiobookService.digest(file);
            if (size != Files.size(file) || modified != Files.getLastModifiedTime(file).toMillis()) throw new IOException("探测期间文件变化，请重试");
            return new AudioTypes.Candidate(file.toString(), root.toString(), group,
                    result.title().isBlank() ? source.getFileName().toString() : result.title(),
                    result.durationMs(), size, modified, hash, result.mimeType(), result.codec(), result.discNumber(), result.trackNumber(), result.chapters(), null);
        } catch (Exception exception) {
            return new AudioTypes.Candidate(path, root.toString(), group, source.getFileName().toString(),
                    0, 0, 0, "", "", "", null, null, List.of(), exception.getMessage());
        }
    }

    private void saveCandidates(String id, List<AudioTypes.Candidate> results) {
        String payload = audio.json(results);
        if (payload.length() > 8 * 1024 * 1024) throw new IllegalArgumentException("单批预览资料超过 8MB，请分批导入");
        store.update("UPDATE audio_import_tasks SET payload=? WHERE id=? AND state='SCANNING'", payload, id);
    }

    private boolean cancelled(String id, long userId) {
        return !"SCANNING".equals(store.one("SELECT state FROM audio_import_tasks WHERE id=? AND user_id=?", id, userId).get("state"));
    }

    public List<Map<String,Object>> history(User user) {
        return store.rows("SELECT id,state,error,created_at,result_edition_id FROM audio_import_tasks WHERE user_id=? ORDER BY created_at DESC LIMIT 20",user.getId());
    }

    public Map<String, Object> task(User user, String id) {
        var task = store.one("SELECT id,state,payload,error,result_edition_id FROM audio_import_tasks WHERE id=? AND user_id=?", id, user.getId());
        task.put("candidates", audio.read(task.remove("payload").toString(), CANDIDATES));
        return task;
    }

    public void cancel(User user, String id) {
        store.one("SELECT id FROM audio_import_tasks WHERE id=? AND user_id=?", id, user.getId());
        store.update("UPDATE audio_import_tasks SET state='CANCELLED' WHERE id=? AND state IN ('SCANNING','READY','FAILED','INTERRUPTED')", id);
    }

    @Transactional
    public Map<String, Object> confirm(User user, AudioTypes.ImportRequest request) throws IOException {
        var task = store.one("SELECT * FROM audio_import_tasks WHERE id=? AND user_id=? FOR UPDATE", request.taskId(), user.getId());
        if ("IMPORTED".equals(task.get("state"))) return Map.of("editionId", task.get("resultEditionId"));
        if (!"READY".equals(task.get("state"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "探测尚未完成或任务不可用");
        var candidates = audio.read(task.get("payload").toString(), CANDIDATES);
        var selected = new ArrayList<AudioTypes.Candidate>();
        if (new HashSet<>(request.paths()).size() != request.paths().size()) throw new IllegalArgumentException("不能重复选择音轨");
        for (String path : request.paths()) {
            var candidate = candidates.stream().filter(c -> c.path().equals(path)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("音轨不属于此任务"));
            if (candidate.error() != null) throw new IllegalArgumentException("请取消选择探测失败的音轨");
            Path file = Path.of(candidate.path()).toRealPath();
            if (!file.startsWith(Path.of(candidate.root()).toRealPath()) || Files.size(file) != candidate.size()
                    || Files.getLastModifiedTime(file).toMillis() != candidate.modifiedMs()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "音轨已变化，请重新探测");
            }
            selected.add(candidate);
        }
        long editionId;
        if (request.editionId() != null) {
            store.edition(user.getId(), request.editionId());
            editionId = request.editionId();
        } else {
            var info = request.edition();
            Book book;
            if (info.bookId() != null) {
                book = books.findByIdAndUserAndDeletedAtIsNull(info.bookId(), user)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "关联作品不存在"));
            } else {
                book = books.saveAndFlush(Book.builder().title(info.title()).author(info.author())
                        .user(user).sourceType(task.get("directoryId") == null ? Book.SourceType.UPLOAD : Book.SourceType.DIRECTORY_SCAN)
                        .description(info.description()).build());
                Path folder = Path.of(selected.getFirst().path()).getParent();
                Path root = Path.of(selected.getFirst().root()).toRealPath();
                for (String filename : List.of("cover.jpg", "cover.png", "folder.jpg")) {
                    Path cover = folder.resolve(filename);
                    if (Files.isRegularFile(cover) && cover.toRealPath().startsWith(root) && Files.size(cover) <= 10L * 1024 * 1024) {
                        try {
                            if (covers.storeExtractedCover(book, Files.readAllBytes(cover), null)) books.saveAndFlush(book);
                        } catch (ResponseStatusException ignored) { /* Invalid optional cover must not reject valid audio. */ }
                        break;
                    }
                }
                if (task.get("directoryId") != null) {
                    store.update("INSERT INTO book_scan_sources(book_id,scan_directory_id,user_id,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING",
                            book.getId(), task.get("directoryId"), user.getId());
                }
            }
            editionId = store.id("INSERT INTO audiobook_editions(book_id,name,narrator,language,description) VALUES(?,?,?,?,?) RETURNING id",
                    book.getId(), info.name(), AudiobookService.clean(info.narrator()),
                    AudiobookService.clean(info.language()), AudiobookService.clean(info.description()));
        }
        int offset = (int) number(store.one("SELECT COALESCE(MAX(sort_order),-1)+1 AS next FROM audio_tracks WHERE edition_id=?", editionId), "next");
        boolean changed = false;
        for (var candidate : selected) {
            var existing = store.rows("SELECT id,file_hash FROM audio_tracks WHERE edition_id=? AND file_path=?", editionId, candidate.path());
            if (!existing.isEmpty()) {
                if (!candidate.hash().equals(existing.getFirst().get("fileHash"))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "已有音轨内容不同，请新建演播版本以保留原进度");
                }
                store.update("UPDATE audio_tracks SET file_size=?,modified_ms=? WHERE id=?",
                        candidate.size(), candidate.modifiedMs(), existing.getFirst().get("id"));
                continue;
            }
            var moved = store.rows("SELECT id,file_path FROM audio_tracks WHERE edition_id=? AND file_hash=?", editionId, candidate.hash());
            if (moved.size() == 1 && !Files.exists(Path.of(moved.getFirst().get("filePath").toString()))) {
                store.update("UPDATE audio_tracks SET file_path=?,source_root=?,file_size=?,modified_ms=? WHERE id=?",
                        candidate.path(), candidate.root(), candidate.size(), candidate.modifiedMs(), moved.getFirst().get("id"));
                changed = true;
                continue;
            }
            if (moved.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "相同指纹存在多个音轨，请单独核对移动文件");
            long trackId = store.id("INSERT INTO audio_tracks(edition_id,title,file_path,source_root,sort_order,duration_ms,"
                    + "file_size,modified_ms,file_hash,mime_type,codec,disc_number,track_number,extracted_chapters) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                    editionId, candidate.title(), candidate.path(), candidate.root(), offset++, candidate.durationMs(), candidate.size(),
                    candidate.modifiedMs(), candidate.hash(), candidate.mimeType(), candidate.codec(), candidate.discNumber(), candidate.trackNumber(), audio.json(candidate.chapters()));
            int chapterOrder = (int) number(store.one("SELECT COUNT(*) AS count FROM audio_chapters WHERE edition_id=?", editionId), "count");
            var chapters = candidate.chapters().isEmpty()
                    ? List.of(new AudioTypes.ProbeChapter(candidate.title(), 0, candidate.durationMs())) : candidate.chapters();
            for (var chapter : chapters) {
                store.update("INSERT INTO audio_chapters(edition_id,title,sort_order,segments) VALUES(?,?,?,?)",
                        editionId, chapter.title(), chapterOrder++, audio.json(List.of(new AudioTypes.Segment(trackId, chapter.startMs(), chapter.endMs()))));
            }
            changed = true;
        }
        if (changed && request.editionId() != null) audio.bump(editionId);
        store.update("UPDATE audio_import_tasks SET state='IMPORTED',result_edition_id=? WHERE id=?", editionId, request.taskId());
        return Map.of("editionId", editionId, "imported", selected.size());
    }

    static boolean supported(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".m4b");
    }

    static int naturalCompare(String left, String right) {
        var a = NUMBER_PARTS.matcher(left.toLowerCase(java.util.Locale.ROOT));
        var b = NUMBER_PARTS.matcher(right.toLowerCase(java.util.Locale.ROOT));
        while (true) {
            boolean hasA = a.find(), hasB = b.find();
            if (!hasA || !hasB) return Boolean.compare(hasA, hasB);
            String x = a.group(), y = b.group();
            int compare = Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))
                    ? new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y)) : x.compareTo(y);
            if (compare != 0) return compare;
        }
    }
}
