package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/** Issues short-lived opaque capabilities for sandboxed Readium iframe resources. */
@Service
public class ReadiumAccessTokenService {

    private final Cache<String, AccessGrant> grants = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(Duration.ofHours(4))
            .build();

    public String issue(Book book, BookVersion version) {
        String token = UUID.randomUUID().toString().replace("-", "");
        grants.put(token, new AccessGrant(
                book.getId(), version.getId(), version.getFormat(), version.getFilePath()));
        return token;
    }

    public BookVersion resolve(String token) {
        AccessGrant grant = grants.getIfPresent(token);
        if (grant == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Readium 资源令牌无效或已过期");
        }
        return BookVersion.builder()
                .id(grant.versionId())
                .format(grant.format())
                .filePath(grant.filePath())
                .build();
    }

    private record AccessGrant(Long bookId, Long versionId, String format, String filePath) {}
}
