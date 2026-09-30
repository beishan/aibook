package com.aibook.controller;

import com.aibook.model.entity.RewriteChapter;
import com.aibook.model.entity.RewriteProject;
import com.aibook.model.entity.User;
import com.aibook.service.RewriteService;
import com.aibook.service.RewriteService.ChapterOverride;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    public record ContentRequest(String content, Long revision) { }
    public record RevisionRequest(Long revision) { }
    public record CompleteRequest(boolean force) { }
    public record ProjectRequest(String name, String description, Long currentChapterId) { }
    public record SplitRequest(int position, String newTitle, Long revision) { }
    public record MergeRequest(String title, Long revision) { }

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

    @PatchMapping("/{projectId}")
    public Map<String, Object> updateProject(Authentication authentication,
            @PathVariable Long projectId, @RequestBody ProjectRequest request) {
        return rewriteService.updateProject(user(authentication), projectId,
                request.name(), request.description(), request.currentChapterId());
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
                request.content(), request.revision());
    }

    @DeleteMapping("/{projectId}/chapters/{chapterId}")
    public ResponseEntity<Void> deleteChapter(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId) {
        rewriteService.deleteChapter(user(authentication), projectId, chapterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/deleted-chapters")
    public List<Map<String, Object>> deletedChapters(Authentication authentication,
            @PathVariable Long projectId) {
        return rewriteService.deletedChapters(user(authentication), projectId);
    }

    @PostMapping("/{projectId}/deleted-chapters/{chapterId}/restore")
    public Map<String, Object> restoreDeletedChapter(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId) {
        return rewriteService.restoreDeletedChapter(user(authentication), projectId, chapterId);
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

    @PostMapping("/{projectId}/chapters/{chapterId}/split")
    public Map<String, Object> split(Authentication authentication,
            @PathVariable Long projectId, @PathVariable Long chapterId,
            @RequestBody SplitRequest request) {
        return rewriteService.splitChapter(user(authentication), projectId, chapterId,
                request.position(), request.newTitle(), request.revision());
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
