package com.aibook.audiobook;

import static com.aibook.audiobook.AudioStore.number;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AudioPlaybackService {
    private final AudioStore store;
    private final AudiobookService audio;
    private final UserRepository users;
    private final AudioOfflineService offline;
    @Value("${APP_TIME_ZONE:Asia/Shanghai}")
    private String timeZone;

    @Transactional
    public Map<String, Object> start(User user, long editionId, boolean takeover) {
        lockAccount(user.getId());
        var edition = store.edition(user.getId(), editionId);
        if (audio.tracks(editionId).isEmpty()) throw new IllegalArgumentException("此版本尚无可播放音轨");
        store.update("INSERT INTO listening_progress(user_id,edition_id) VALUES(?,?) ON CONFLICT DO NOTHING", user.getId(), editionId);
        var progress = store.one("SELECT * FROM listening_progress WHERE user_id=? AND edition_id=? FOR UPDATE", user.getId(), editionId);
        if (progress.get("activeSession") != null) {
            var active = store.rows("SELECT id FROM listening_sessions WHERE id=? AND ended=false AND expires_at>CURRENT_TIMESTAMP",
                    progress.get("activeSession"));
            if (!active.isEmpty() && !takeover) throw conflict("另一收听会话仍有效，是否接管并使用服务器进度？");
        }
        store.update("UPDATE listening_sessions SET ended=true WHERE user_id=? AND ended=false", user.getId());
        // One account has a single active player. An obsolete device cannot resume its writes.
        String session = UUID.randomUUID().toString();
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.update("INSERT INTO listening_sessions(id,user_id,edition_id,credential_hash,content_revision,expires_at) "
                + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP + INTERVAL '2 hours')", session, user.getId(), editionId, hash(key), edition.get("contentRevision"));
        store.update("UPDATE listening_progress SET active_session=?,revision=revision+1 WHERE user_id=? AND edition_id=?",
                session, user.getId(), editionId);
        return Map.of("sessionId", session, "key", key, "expiresInSeconds", 7200,
                "contentRevision", edition.get("contentRevision"), "progress", audio.progress(user.getId(), editionId));
    }

    @Transactional
    public Map<String, Object> heartbeat(User user, String id, AudioTypes.Heartbeat event) {
        lockAccount(user.getId());
        var session = ownedSession(user, id);
        long editionId = number(session, "editionId");
        var edition = store.edition(user.getId(), editionId);
        var progress = audio.progress(user.getId(), editionId);
        if (!id.equals(progress.get("activeSession"))) throw conflict("播放已被其他设备接管");
        if (event.contentRevision() != number(edition, "contentRevision")
                || event.contentRevision() != number(session, "contentRevision")) throw conflict("音轨内容已更新，请重新打开播放器");
        if (event.sequence() <= number(session, "lastSequence")) return progress;
        if (event.revision() != number(progress, "revision")) throw conflict("收听位置已更新，请重新获取服务器进度");
        var track = store.one("SELECT duration_ms FROM audio_tracks WHERE id=? AND edition_id=?", event.trackId(), editionId);
        if (event.positionMs() > number(track, "durationMs")) throw new IllegalArgumentException("位置超出音轨时长");
        Instant now = Instant.now();
        Instant previous = Instant.parse(session.get("lastEventAt").toString());
        long requested = Math.max(0, event.elapsedMs() - number(session, "elapsedMs"));
        long wall = Math.max(0, now.toEpochMilli() - previous.toEpochMilli());
        long elapsed = Math.min(requested, Math.min(wall, 30000));
        // Account lock serializes all editions; only one valid session can accumulate time.
        store.update("UPDATE listening_sessions SET last_sequence=?,elapsed_ms=?,last_event_at=?,"
                + "expires_at=CURRENT_TIMESTAMP + INTERVAL '2 hours' WHERE id=?",
                event.sequence(), Math.max(event.elapsedMs(), number(session, "elapsedMs")), java.sql.Timestamp.from(now), id);
        store.update("UPDATE listening_progress SET track_id=?,position_ms=?,revision=revision+1,status=?,updated_at=? "
                + "WHERE user_id=? AND edition_id=?", event.trackId(), event.positionMs(),
                event.finished() ? "FINISHED" : "LISTENING", java.sql.Timestamp.from(now), user.getId(), editionId);
        offline.account(user.getId(), now.toEpochMilli() - elapsed, now.toEpochMilli());
        return audio.progress(user.getId(), editionId);
    }

    @Transactional
    public void end(User user, String sessionId) {
        lockAccount(user.getId());
        // Ending is idempotent even if another session already took over.
        var row = store.one("SELECT edition_id FROM listening_sessions WHERE id=? AND user_id=?", sessionId, user.getId());
        store.update("UPDATE listening_sessions SET ended=true WHERE id=?", sessionId);
        store.update("UPDATE listening_progress SET active_session=NULL WHERE user_id=? AND edition_id=? AND active_session=?",
                user.getId(), row.get("editionId"), sessionId);
    }

    public Map<String, Object> media(String sessionId, long trackId, String key) {
        if (key == null || key.length() > 100) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "播放凭证无效");
        var session = store.one("SELECT * FROM listening_sessions WHERE id=? AND ended=false AND expires_at>CURRENT_TIMESTAMP", sessionId);
        if (!MessageDigest.isEqual(hash(key).getBytes(StandardCharsets.UTF_8),
                session.get("credentialHash").toString().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "播放凭证无效");
        }
        long userId = number(session, "userId");
        User user = users.findById(userId).filter(User::isEnabled)
                .filter(User::isAccountNonLocked).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账户不可用"));
        var edition = store.edition(userId, number(session, "editionId"));
        if (number(edition, "contentRevision") != number(session, "contentRevision")) throw conflict("音频内容已更新");
        return store.one("SELECT * FROM audio_tracks WHERE id=? AND edition_id=?", trackId, session.get("editionId"));
    }

    @Transactional
    public void setStatus(User user, long id, boolean finished) {
        lockAccount(user.getId());
        store.edition(user.getId(), id);
        var tracks = audio.tracks(id);
        if (tracks.isEmpty()) throw new IllegalArgumentException("此版本暂无音轨");
        var track = finished ? tracks.getLast() : tracks.getFirst();
        store.update("UPDATE listening_sessions SET ended=true WHERE user_id=? AND edition_id=?", user.getId(), id);
        store.update("INSERT INTO listening_progress(user_id,edition_id,track_id,position_ms,status) VALUES(?,?,?,?,?) "
                + "ON CONFLICT(user_id,edition_id) DO UPDATE SET track_id=EXCLUDED.track_id,position_ms=EXCLUDED.position_ms,"
                + "status=EXCLUDED.status,active_session=NULL,revision=listening_progress.revision+1,updated_at=CURRENT_TIMESTAMP",
                user.getId(), id, track.get("id"), finished ? number(track,"durationMs") : 0L, finished ? "FINISHED" : "NEW");
    }

    public AudioTypes.Settings settings(User user) {
        var row = store.rows("SELECT audio_settings FROM user_preferences WHERE user_id=?", user.getId());
        if (row.isEmpty() || row.getFirst().get("audioSettings") == null) return new AudioTypes.Settings(1, 15, 30, 30);
        return audio.read(row.getFirst().get("audioSettings").toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    @Transactional
    public void settings(User user, AudioTypes.Settings settings) {
        lockAccount(user.getId());
        store.update("INSERT INTO user_preferences(user_id,created_at,updated_at,audio_settings) VALUES(?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?) "
                + "ON CONFLICT(user_id) DO UPDATE SET audio_settings=EXCLUDED.audio_settings,updated_at=CURRENT_TIMESTAMP",
                user.getId(), audio.json(settings));
    }

    public List<Map<String, Object>> activity(User user) {
        var today = java.sql.Date.valueOf(java.time.LocalDate.now(ZoneId.of(timeZone)));
        var since = java.sql.Date.valueOf(today.toLocalDate().minusDays(364));
        return store.rows("SELECT activity_date,elapsed_ms,(?::date-activity_date) AS days_ago FROM listening_daily_activity "
                + "WHERE user_id=? AND activity_date>=? AND activity_date<=? ORDER BY activity_date DESC",today,user.getId(),since,today);
    }

    private Map<String, Object> ownedSession(User user, String id) {
        return store.one("SELECT * FROM listening_sessions WHERE id=? AND user_id=? AND ended=false AND expires_at>CURRENT_TIMESTAMP",
                id, user.getId());
    }

    private void lockAccount(long userId) {
        store.one("SELECT id FROM users WHERE id=? FOR UPDATE", userId);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
