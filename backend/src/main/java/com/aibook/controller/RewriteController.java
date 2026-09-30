package com.aibook.controller;

import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteProject;
import com.aibook.model.entity.RewriteMemo;
import com.aibook.model.entity.User;
import com.aibook.service.RewriteService;
import com.aibook.service.RewriteService.ChapterOverride;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/rewrite/projects")
@RequiredArgsConstructor
public class RewriteController {

    private final RewriteService rewriteService;
    private final UserService userService;

    public record PreviewRequest(Long bookId, Long sourceVersionId) { }
    public record CreateRequest(String previewToken, String name, String versionName,
                                String description, boolean singleChapter,
                                List<ChapterOverride> chapterOverrides) { }
    public record ChapterRequest(String title, String volumeTitle,
                                 RewriteChapter.Status status,
                                 Long revision) { }
    public record ContentRequest(String content, Integer contentFormatVersion, Long revision) { }
    public record RevisionRequest(Long revision) { }
    public record CompleteRequest(boolean force) { }
    public record ChapterIdsRequest(List<Long> chapterIds) { }
    public record ProjectRequest(String name, String description, Long currentChapterId,
                                 Integer currentChapterPosition) { }
    public record SplitRequest(int position, String newTitle, String beforeContent,
                               String afterContent, Long revision) { }
    public record MergeRequest(String title, Long revision) { }
    public record SnapshotRequest(String name) { }
    public record MemoRequest(RewriteMemo.Type type, String title, String content,
                             Long chapterId, Integer anchorPosition,
                             RewriteMemo.State state, List<String> aliases) { }
    public record BulkChapterRequest(List<Long> chapterIds, String action, String value,
                                     Map<Long, Long> revisions) { }
    public record SearchRequest(String query, String scope, Long chapterId,
                                String volumeTitle, boolean matchCase,
                                boolean wholeWord, boolean regex,
                                String replacement, Map<Long, Long> revisions) {
        RewriteService.SearchOptions options() {
            return new RewriteService.SearchOptions(query, scope, chapterId, volumeTitle,
                    matchCase, wholeWord, regex);
        }
    }

    @GetMapping
    public Page<Map<String, Object>> list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) RewriteProject.Status status,
            @RequestParam(required = false) String keyword) {
        return rewriteService.list(user(authentication), page, size, status, keyword);
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(Authentication authentication,
            @RequestBody PreviewRequest request) {
        return rewriteService.preview(user(authentication), request.bookId(),
                request.sourceVersionId());
    }

    @PostMapping
    public Map<String, Object> create(Authentication authentication,
            @RequestBody CreateRequest request) {
        return rewriteService.create(user(authentication), request.previewToken(),
                request.name(), request.versionName(), request.description(),
                request.singleChapter(), request.chapterOverrides());
    }

    @GetMapping("/{projectId}")
    public Map<String, Object> get(Authentication authentication, @PathVariable Long projectId) {
        return rewriteService.get(user(authentication), projectId);
    }

    @GetMapping("/{projectId}/export")
    public ResponseEntity<byte[]> export(Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam String format,
            @RequestParam(defaultValue = "true") boolean includeMetadata,
            @RequestParam(defaultValue = "true") boolean includeChapterTitles,
            @RequestParam(defaultValue = "ORIGINAL") String chapterTitleStyle,
            @RequestParam(defaultValue = "1") int chapterSpacing) {
        RewriteService.ExportFile file = rewriteService.export(user(authentication), projectId,
                format, includeMetadata, includeChapterTitles, chapterTitleStyle, chapterSpacing);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.body());
    }

    @PatchMapping("/{projectId}")
    public Map<String, Object> updateProject(Authentication authentication,
            @PathVariable Long projectId, @RequestBody ProjectRequest request) {
        return rewriteService.updateProject(user(authentication), projectId,
                request.name(), request.description(), request.currentChapterId(),
                request.currentChapterPosition());
    }

    @GetMapping("/{projectId}/chapters")
    public List<Map<String, Object>> chapters(Authentication authentication,
            @PathVariable Long projectId) {
        return rewriteService.chapterList(user(authentication), projectId);
    }

    @PostMapping("/{projectId}/chapters")
    public Map<String, Object> addChapter(Authentication authentication,
            @PathVariable Long projectId, @RequestBody ChapterRequest request) {
        return rewriteService.addChapter(user(authentication), projectId,
                request.title(), request.volumeTitle());
    }

    @GetMapping("/{projectId}/chapters/{chapterId}")
    public Map<String, Object> chapter(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId) {
        return rewriteService.chapter(user(authentication), projectId, chapterId);
    }

    @PatchMapping("/{projectId}/chapters/{chapterId}")
    public Map<String, Object> updateChapter(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody ChapterRequest request) {
        return rewriteService.updateChapter(user(authentication), projectId, chapterId,
                request.title(), request.status(), request.volumeTitle(), request.revision());
    }

    @PutMapping("/{projectId}/chapters/{chapterId}/content")
    public Map<String, Object> saveContent(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody ContentRequest request) {
        return rewriteService.saveContent(user(authentication), projectId, chapterId,
                request.content(), request.contentFormatVersion(), request.revision());
    }

    @DeleteMapping("/{projectId}/chapters/{chapterId}")
    public ResponseEntity<Void> deleteChapter(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestParam Long revision) {
        rewriteService.deleteChapter(user(authentication), projectId, chapterId, revision);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/deleted-chapters")
    public List<Map<String, Object>> deletedChapters(Authentication authentication,
            @PathVariable Long projectId) {
        return rewriteService.deletedChapters(user(authentication), projectId);
    }

    @PostMapping("/{projectId}/deleted-chapters/{chapterId}/restore")
    public Map<String, Object> restoreDeletedChapter(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody RevisionRequest request) {
        return rewriteService.restoreDeletedChapter(user(authentication), projectId, chapterId,
                request.revision());
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam boolean deleteVersion) {
        if (!deleteVersion) {
            return ResponseEntity.badRequest().build();
        }
        rewriteService.deleteProjectAndVersion(user(authentication), projectId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{projectId}/chapters/order")
    public ResponseEntity<Void> reorder(Authentication authentication,
            @PathVariable Long projectId, @RequestBody List<Long> chapterIds) {
        rewriteService.reorder(user(authentication), projectId, chapterIds);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/chapters/copy")
    public ResponseEntity<String> copyChapterContents(Authentication authentication,
            @PathVariable Long projectId, @RequestBody ChapterIdsRequest request) {
        return ResponseEntity.ok(rewriteService.copyChapterContents(user(authentication),
                projectId, request.chapterIds()));
    }

    @PostMapping("/{projectId}/chapters/bulk")
    public Map<String, Object> bulkChapters(Authentication authentication,
            @PathVariable Long projectId, @RequestBody BulkChapterRequest request) {
        return rewriteService.bulkChapters(user(authentication), projectId,
                new RewriteService.BulkRequest(request.chapterIds(), request.action(),
                        request.value(), request.revisions()));
    }

    @GetMapping("/{projectId}/snapshots")
    public List<Map<String, Object>> snapshots(Authentication authentication,
            @PathVariable Long projectId) {
        return rewriteService.listSnapshots(user(authentication), projectId);
    }

    @GetMapping("/{projectId}/snapshots/storage")
    public Map<String, Object> snapshotStorage(Authentication authentication,
            @PathVariable Long projectId) {
        return rewriteService.snapshotStorage(user(authentication), projectId);
    }

    @PostMapping("/{projectId}/snapshots")
    public Map<String, Object> createSnapshot(Authentication authentication,
            @PathVariable Long projectId, @RequestBody SnapshotRequest request) {
        return rewriteService.createSnapshot(user(authentication), projectId, request.name());
    }

    @DeleteMapping("/{projectId}/snapshots/{snapshotId}")
    public ResponseEntity<Void> deleteSnapshot(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long snapshotId) {
        rewriteService.deleteNamedSnapshot(user(authentication), projectId, snapshotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/snapshots/{snapshotId}/preview")
    public Map<String, Object> previewSnapshotRestore(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long snapshotId) {
        return rewriteService.previewSnapshotRestore(user(authentication), projectId, snapshotId);
    }

    @PostMapping("/{projectId}/snapshots/{snapshotId}/restore")
    public Map<String, Object> restoreSnapshot(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long snapshotId) {
        return rewriteService.restoreSnapshot(user(authentication), projectId, snapshotId);
    }

    @GetMapping("/{projectId}/memos")
    public List<Map<String, Object>> memos(Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(required = false) RewriteMemo.Type type) {
        return rewriteService.listMemos(user(authentication), projectId, type);
    }

    @PostMapping("/{projectId}/memos")
    public Map<String, Object> createMemo(Authentication authentication,
            @PathVariable Long projectId, @RequestBody MemoRequest request) {
        return rewriteService.saveMemo(user(authentication), projectId,
                new RewriteService.MemoRequest(null, request.type(), request.title(),
                        request.content(), request.chapterId(), request.anchorPosition(),
                        request.state(), request.aliases()));
    }

    @PutMapping("/{projectId}/memos/{memoId}")
    public Map<String, Object> updateMemo(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long memoId,
            @RequestBody MemoRequest request) {
        return rewriteService.saveMemo(user(authentication), projectId,
                new RewriteService.MemoRequest(memoId, request.type(), request.title(),
                        request.content(), request.chapterId(), request.anchorPosition(),
                        request.state(), request.aliases()));
    }

    @DeleteMapping("/{projectId}/memos/{memoId}")
    public ResponseEntity<Void> deleteMemo(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long memoId) {
        rewriteService.deleteMemo(user(authentication), projectId, memoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/search")
    public Map<String, Object> search(Authentication authentication,
            @PathVariable Long projectId, @RequestBody SearchRequest request) {
        return rewriteService.search(user(authentication), projectId, request.options(),
                request.replacement());
    }

    @PostMapping("/{projectId}/replace")
    public Map<String, Object> replace(Authentication authentication,
            @PathVariable Long projectId, @RequestBody SearchRequest request) {
        return rewriteService.replace(user(authentication), projectId, request.options(),
                request.replacement(), request.revisions());
    }

    @PostMapping("/{projectId}/chapters/{chapterId}/split")
    public Map<String, Object> split(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody SplitRequest request) {
        return rewriteService.splitChapter(user(authentication), projectId, chapterId,
                request.position(), request.newTitle(), request.beforeContent(),
                request.afterContent(), request.revision());
    }

    @PostMapping("/{projectId}/chapters/{chapterId}/merge-next")
    public Map<String, Object> mergeNext(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody MergeRequest request) {
        return rewriteService.mergeWithNext(user(authentication), projectId, chapterId,
                request.title(), request.revision());
    }

    @GetMapping("/{projectId}/chapters/{chapterId}/source")
    public Map<String, Object> source(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId) {
        Map<String, Object> chapter = rewriteService.chapter(user(authentication),
                projectId, chapterId);
        return Map.of("title", chapter.get("sourceTitle") == null
                        ? "" : chapter.get("sourceTitle"),
                "content", chapter.get("sourceContent") == null
                        ? "" : chapter.get("sourceContent"));
    }

    @GetMapping("/{projectId}/chapters/{chapterId}/revisions")
    public List<Map<String, Object>> revisions(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId) {
        return rewriteService.revisions(user(authentication), projectId, chapterId);
    }

    @GetMapping("/{projectId}/chapters/{chapterId}/revisions/{revisionNumber}")
    public Map<String, Object> revision(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @PathVariable Long revisionNumber) {
        return rewriteService.revision(user(authentication), projectId, chapterId, revisionNumber);
    }

    @PostMapping("/{projectId}/chapters/{chapterId}/revisions/{revisionNumber}/restore")
    public Map<String, Object> restoreRevision(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @PathVariable Long revisionNumber, @RequestBody RevisionRequest request) {
        return rewriteService.restoreRevision(user(authentication), projectId,
                chapterId, revisionNumber, request.revision());
    }

    @PostMapping("/{projectId}/chapters/{chapterId}/restore-source")
    public Map<String, Object> restoreSource(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody RevisionRequest request) {
        return rewriteService.restoreSource(user(authentication), projectId, chapterId,
                request.revision());
    }

    @PostMapping("/{projectId}/pause")
    public Map<String, Object> pause(Authentication authentication, @PathVariable Long projectId) {
        return rewriteService.changeStatus(user(authentication), projectId,
                RewriteProject.Status.PAUSED, false);
    }

    @PostMapping("/{projectId}/resume")
    public Map<String, Object> resume(Authentication authentication, @PathVariable Long projectId) {
        return rewriteService.changeStatus(user(authentication), projectId,
                RewriteProject.Status.ACTIVE, false);
    }

    @PostMapping("/{projectId}/archive")
    public Map<String, Object> archive(Authentication authentication, @PathVariable Long projectId) {
        return rewriteService.changeStatus(user(authentication), projectId,
                RewriteProject.Status.ARCHIVED, false);
    }

    @PostMapping("/{projectId}/complete")
    public Map<String, Object> complete(Authentication authentication,
            @PathVariable Long projectId, @RequestBody CompleteRequest request) {
        return rewriteService.changeStatus(user(authentication), projectId,
                RewriteProject.Status.COMPLETED, request.force());
    }

    private User user(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
