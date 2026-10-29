package com.aibook.audiobook;

import static com.aibook.audiobook.AudioStore.number;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class AudioController {
    private final UserService users;
    private final AudiobookService audio;
    private final AudioImportService imports;
    private final AudioPlaybackService playback;
    private final AudioOfflineService offline;

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(jakarta.validation.ConstraintViolationException exception) {
        return Map.of("message","音频请求参数无效");
    }

    private User user(Authentication auth) { return users.findByUsername(auth.getName()); }

    @GetMapping("/audiobooks")
    public Map<String, Object> list(Authentication auth, @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "false") boolean favorite) {
        return audio.list(user(auth), keyword, status, page, size, favorite);
    }

    @GetMapping("/audiobooks/by-book/{bookId}")
    public List<Map<String, Object>> byBook(Authentication auth, @PathVariable long bookId) {
        return audio.byBook(user(auth), bookId);
    }

    @GetMapping("/audiobooks/{id}")
    public Map<String, Object> detail(Authentication auth, @PathVariable long id) { return audio.detail(user(auth), id); }

    @PutMapping("/audiobooks/{id}")
    public Map<String, Object> edit(Authentication auth, @PathVariable long id, @Valid @RequestBody AudioTypes.EditionRequest body) {
        return audio.edit(user(auth), id, body);
    }

    @DeleteMapping("/audiobooks/{id}")
    public void recycle(Authentication auth, @PathVariable long id) { audio.recycle(user(auth), id, false); }

    @PostMapping("/audiobooks/{id}/restore")
    public void restore(Authentication auth, @PathVariable long id) { audio.recycle(user(auth), id, true); }

    @PutMapping("/audiobooks/{id}/tracks/order")
    public void reorder(Authentication auth, @PathVariable long id, @RequestBody @Size(min=1,max=5000) List<Long> ids) {
        audio.reorder(user(auth), id, ids);
    }

    @PutMapping("/audiobooks/{id}/tracks/{trackId}")
    public void editTrack(Authentication auth, @PathVariable long id, @PathVariable long trackId,
            @Valid @RequestBody AudioTypes.TrackEdit body) { audio.editTrack(user(auth), id, trackId, body.title()); }

    @PutMapping("/audiobooks/{id}/chapters")
    public void chapters(Authentication auth, @PathVariable long id,
            @Valid @RequestBody List<AudioTypes.ChapterEdit> chapters) { audio.chapters(user(auth), id, chapters); }

    @PostMapping("/audiobooks/imports/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> scan(Authentication auth, @Valid @RequestBody AudioTypes.ScanRequest body) throws IOException {
        return imports.scan(user(auth), body);
    }

    @PostMapping("/audiobooks/imports/upload")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> upload(Authentication auth, @RequestParam("files") List<MultipartFile> files) throws IOException {
        return imports.upload(user(auth), files);
    }

    @GetMapping("/audiobooks/imports")
    public List<Map<String,Object>> imports(Authentication auth) { return imports.history(user(auth)); }

    @GetMapping("/audiobooks/imports/{taskId}")
    public Map<String, Object> task(Authentication auth, @PathVariable String taskId) { return imports.task(user(auth), taskId); }

    @DeleteMapping("/audiobooks/imports/{taskId}")
    public void cancel(Authentication auth, @PathVariable String taskId) { imports.cancel(user(auth), taskId); }

    @PostMapping("/audiobooks/imports/confirm")
    public Map<String, Object> confirm(Authentication auth, @Valid @RequestBody AudioTypes.ImportRequest body) throws IOException {
        return imports.confirm(user(auth), body);
    }

    @GetMapping("/audiobooks/{id}/offline-manifest")
    public Map<String,Object> manifest(Authentication auth, @PathVariable long id) { return offline.manifest(user(auth),id); }

    @PostMapping("/audiobooks/{id}/offline-progress")
    public Map<String,Object> offlineProgress(Authentication auth, @PathVariable long id,
            @Valid @RequestBody AudioOfflineService.OfflineProgress body) { return offline.apply(user(auth),id,body); }

    @RequestMapping(value="/audiobooks/{id}/tracks/{trackId}/download",method={RequestMethod.GET,RequestMethod.HEAD})
    public void download(Authentication auth,@PathVariable long id,@PathVariable long trackId,
            HttpServletRequest request,HttpServletResponse response) throws IOException {
        var track = audio.downloadTrack(user(auth),id,trackId);
        writeMedia(track,request,response);
    }

    @PostMapping("/audiobooks/{id}/sessions")
    public Map<String, Object> start(Authentication auth, @PathVariable long id, @RequestBody AudioTypes.SessionRequest body) {
        return playback.start(user(auth), id, body.takeover());
    }

    @PutMapping("/audiobooks/sessions/{sessionId}")
    public Map<String, Object> heartbeat(Authentication auth, @PathVariable String sessionId,
            @Valid @RequestBody AudioTypes.Heartbeat body) { return playback.heartbeat(user(auth), sessionId, body); }

    @DeleteMapping("/audiobooks/sessions/{sessionId}")
    public void end(Authentication auth, @PathVariable String sessionId) { playback.end(user(auth), sessionId); }

    @PostMapping("/audiobooks/{id}/progress/status")
    public void setStatus(Authentication auth, @PathVariable long id, @RequestParam boolean finished) {
        playback.setStatus(user(auth), id, finished);
    }

    @GetMapping("/audiobooks/settings")
    public AudioTypes.Settings settings(Authentication auth) { return playback.settings(user(auth)); }

    @PutMapping("/audiobooks/settings")
    public void settings(Authentication auth, @Valid @RequestBody AudioTypes.Settings body) { playback.settings(user(auth), body); }

    @GetMapping("/audiobooks/activity")
    public List<Map<String, Object>> activity(Authentication auth) { return playback.activity(user(auth)); }

    @PostMapping("/audiobooks/{id}/bookmarks")
    public void bookmark(Authentication auth, @PathVariable long id, @Valid @RequestBody AudioTypes.BookmarkRequest body) {
        audio.bookmark(user(auth), id, body);
    }

    @DeleteMapping("/audiobooks/bookmarks/{bookmarkId}")
    public void deleteBookmark(Authentication auth, @PathVariable long bookmarkId) { audio.deleteBookmark(user(auth), bookmarkId); }

    @RequestMapping(value = "/audio-media/{sessionId}/{trackId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void media(@PathVariable String sessionId, @PathVariable long trackId,
            @RequestParam String key, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var track = playback.media(sessionId, trackId, key);
        writeMedia(track,request,response);
    }

    private void writeMedia(Map<String,Object> track,HttpServletRequest request,HttpServletResponse response) throws IOException {
        java.nio.file.Path path;
        try { path = audio.checkedPath(track); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音轨缺失或不可读取"); }
        long size = number(track, "fileSize");
        String etag = "\"" + track.get("fileHash") + "\"";
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", etag);
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setDateHeader("Last-Modified", number(track, "modifiedMs"));
        response.setContentType(track.get("mimeType").toString());
        String header = request.getHeader("Range");
        String ifRange = request.getHeader("If-Range");
        if (ifRange != null && !ifRange.equals(etag)) {
            try {
                if (request.getDateHeader("If-Range") < number(track, "modifiedMs") / 1000 * 1000) header = null;
            } catch (IllegalArgumentException exception) { header = null; }
        }
        AudioRange range;
        try { range = AudioRange.parse(header, size); }
        catch (IllegalArgumentException exception) {
            response.setStatus(416); response.setHeader("Content-Range", "bytes */" + size); return;
        }
        if (range.partial()) {
            response.setStatus(206);
            response.setHeader("Content-Range", "bytes " + range.start() + "-" + range.end() + "/" + size);
        }
        response.setContentLengthLong(range.length());
        if ("HEAD".equals(request.getMethod())) return;
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            channel.position(range.start());
            long remaining = range.length();
            var buffer = ByteBuffer.allocate(65536);
            var output = response.getOutputStream();
            while (remaining > 0) {
                buffer.clear(); buffer.limit((int) Math.min(buffer.capacity(), remaining));
                int count = channel.read(buffer);
                if (count < 0) break;
                output.write(buffer.array(), 0, count);
                remaining -= count;
            }
        }
    }
}
