package com.aibook.audiobook;

import static com.aibook.audiobook.AudioStore.number;

import com.aibook.model.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AudioOfflineService {
    private final AudioStore store;
    private final AudiobookService audio;
    @Value("${APP_TIME_ZONE:Asia/Shanghai}") private String zone;

    public record Interval(@Min(0) long startMs, @Min(1) long endMs) {}
    public record OfflineProgress(@NotBlank @Size(max=64) String eventId,
            @Min(0) long baseRevision, @Min(1) long contentRevision,
            @NotNull Long trackId, @Min(0) long positionMs, boolean finished,
            boolean resolveConflict, @Size(max=1000) List<@Valid Interval> intervals) {}

    public Map<String, Object> manifest(User user, long id) {
        var result = audio.detail(user, id);
        result.put("tracks", audio.tracks(id).stream().map(track -> Map.of(
                "id", track.get("id"), "title", track.get("title"), "durationMs", track.get("durationMs"),
                "mimeType", track.get("mimeType"), "fileSize", track.get("fileSize"), "fileHash", track.get("fileHash"))).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> apply(User user, long id, OfflineProgress event) {
        store.one("SELECT id FROM users WHERE id=? FOR UPDATE", user.getId());
        var edition = store.edition(user.getId(), id);
        var replay = store.rows("SELECT applied_revision,edition_id FROM audio_offline_events WHERE user_id=? AND event_id=?", user.getId(), event.eventId());
        if (!replay.isEmpty()) {
            if (number(replay.getFirst(),"editionId") != id) throw new IllegalArgumentException("离线事件属于另一版本");
            return audio.progress(user.getId(), id);
        }
        var progress = audio.progress(user.getId(), id);
        if (number(edition,"contentRevision") != event.contentRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"音轨版本已变化，离线位置保留；请重新下载或核对音轨");
        }
        if (number(progress,"revision") != event.baseRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"另一设备已有新进度，请保留离线位置并选择使用哪一份");
        }
        var active = store.rows("SELECT id FROM listening_sessions WHERE user_id=? AND ended=false AND expires_at>CURRENT_TIMESTAMP", user.getId());
        if (!active.isEmpty() && !event.resolveConflict()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"账户有在线播放会话，是否结束在线会话并采用离线位置？");
        }
        var track = store.one("SELECT duration_ms FROM audio_tracks WHERE id=? AND edition_id=?", event.trackId(), id);
        if (event.positionMs() > number(track,"durationMs")) throw new IllegalArgumentException("离线位置超出音轨");
        var intervals = event.intervals() == null ? List.<Interval>of() : event.intervals();
        long now = Instant.now().toEpochMilli();
        for (var interval : intervals) {
            if (interval.startMs() >= interval.endMs() || interval.endMs() > now + 300000
                    || interval.startMs() < now - 30L*86400000 || interval.endMs() - interval.startMs() > 86400000) {
                throw new IllegalArgumentException("离线时段无效或设备时间不正确；保留位置并校准设备时间后重试");
            }
        }
        store.update("UPDATE listening_sessions SET ended=true WHERE user_id=?", user.getId());
        store.update("INSERT INTO listening_progress(user_id,edition_id,track_id,position_ms,status,revision) VALUES(?,?,?,?,?,1) "
                + "ON CONFLICT(user_id,edition_id) DO UPDATE SET track_id=EXCLUDED.track_id,position_ms=EXCLUDED.position_ms,"
                + "status=EXCLUDED.status,active_session=NULL,revision=listening_progress.revision+1,updated_at=CURRENT_TIMESTAMP",
                user.getId(),id,event.trackId(),event.positionMs(),event.finished()?"FINISHED":"LISTENING");
        for (var interval : intervals) account(user.getId(), interval.startMs(), interval.endMs());
        var updated = audio.progress(user.getId(), id);
        store.update("INSERT INTO audio_offline_events(user_id,event_id,edition_id,applied_revision) VALUES(?,?,?,?)",
                user.getId(), event.eventId(),id,updated.get("revision"));
        return updated;
    }

    /** Account lock is held by callers; subtract already claimed intervals across all devices. */
    public void account(long userId, long start, long end) {
        if (start >= end) return;
        var previous = store.rows("SELECT start_ms,end_ms FROM audio_listening_intervals WHERE user_id=? AND end_ms>? AND start_ms<? ORDER BY start_ms", userId,start,end);
        long cursor = start;
        for (var interval : previous) {
            long a = Math.max(start,number(interval,"startMs")), b = Math.min(end,number(interval,"endMs"));
            if (a > cursor) record(userId,cursor,a);
            cursor = Math.max(cursor,b);
        }
        if (cursor < end) record(userId,cursor,end);
        store.update("INSERT INTO audio_listening_intervals(user_id,start_ms,end_ms) VALUES(?,?,?) ON CONFLICT DO NOTHING",userId,start,end);
    }

    private void record(long userId,long start,long end) {
        var timezone = ZoneId.of(zone);
        long cursor = start;
        while (cursor < end) {
            var date = Instant.ofEpochMilli(cursor).atZone(timezone).toLocalDate();
            long next = Math.min(end,date.plusDays(1).atStartOfDay(timezone).toInstant().toEpochMilli());
            store.update("INSERT INTO listening_daily_activity(user_id,activity_date,elapsed_ms) VALUES(?,?,?) "
                    + "ON CONFLICT(user_id,activity_date) DO UPDATE SET elapsed_ms=listening_daily_activity.elapsed_ms+EXCLUDED.elapsed_ms",
                    userId,java.sql.Date.valueOf(date),next-cursor);
            cursor = next;
        }
    }
}
