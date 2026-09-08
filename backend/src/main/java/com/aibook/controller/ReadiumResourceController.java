package com.aibook.controller;

import com.aibook.model.entity.BookVersion;
import com.aibook.service.ReadiumAccessTokenService;
import com.aibook.service.ReadiumPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;

/** Serves resources only when presented with a short-lived opaque Readium capability. */
@RestController
@RequestMapping("/api/readium-resources")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReadiumResourceController {

    private final ReadiumAccessTokenService accessTokenService;
    private final ReadiumPublicationService publicationService;

    @GetMapping("/{accessToken}/{*resourcePath}")
    public ResponseEntity<ByteArrayResource> getResource(
            @PathVariable String accessToken,
            @PathVariable String resourcePath) throws IOException {
        BookVersion version = accessTokenService.resolve(accessToken);
        String archivePath = resourcePath.replaceFirst("^/?resources/", "");
        ReadiumPublicationService.ResourceData data =
                publicationService.readResource(version, archivePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.mediaType()))
                .contentLength(data.bytes().length)
                .eTag(data.etag())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(data.bytes()));
    }
}
