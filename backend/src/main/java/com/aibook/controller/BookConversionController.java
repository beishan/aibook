package com.aibook.controller;

import com.aibook.dto.*;
import com.aibook.model.entity.User;
import com.aibook.service.BookConversionService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/conversions")
@RequiredArgsConstructor
public class BookConversionController {
    private final BookConversionService conversionService;
    private final UserService userService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookConversionTaskDTO upload(Authentication authentication, @RequestParam("file") MultipartFile file) {
        return conversionService.createFromUpload(user(authentication), file);
    }

    @PostMapping("/from-book")
    public BookConversionTaskDTO fromBook(Authentication authentication, @RequestBody Map<String, Long> body) {
        return conversionService.createFromBook(user(authentication), body.get("bookId"), body.get("versionId"));
    }

    @GetMapping
    public List<BookConversionTaskDTO> list(Authentication authentication) {
        return conversionService.list(user(authentication));
    }

    @GetMapping("/{id}")
    public BookConversionTaskDTO get(Authentication authentication, @PathVariable Long id) {
        return conversionService.get(user(authentication), id);
    }

    @PutMapping("/{id}")
    public BookConversionTaskDTO update(Authentication authentication, @PathVariable Long id,
            @RequestBody BookConversionUpdateRequest request) {
        return conversionService.update(user(authentication), id, request);
    }

    @PostMapping("/{id}/analyze-chapters")
    public BookConversionTaskDTO analyzeChapters(Authentication authentication, @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return conversionService.reanalyze(user(authentication), id, body.get("pattern"));
    }

    @PostMapping("/{id}/format-chapters")
    public BookConversionTaskDTO formatChapters(Authentication authentication, @PathVariable Long id,
            @RequestBody BookConversionUpdateRequest request) {
        return conversionService.formatChapters(user(authentication), id, request);
    }

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookConversionTaskDTO uploadCover(Authentication authentication, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return conversionService.uploadCover(user(authentication), id, file);
    }

    @PostMapping("/{id}/cover/library/{coverId}")
    public BookConversionTaskDTO libraryCover(Authentication authentication, @PathVariable Long id,
            @PathVariable Long coverId) {
        return conversionService.chooseLibraryCover(user(authentication), id, coverId);
    }

    @PostMapping("/{id}/cover/random")
    public BookConversionTaskDTO randomCover(Authentication authentication, @PathVariable Long id) {
        return conversionService.randomCover(user(authentication), id);
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<FileSystemResource> cover(Authentication authentication, @PathVariable Long id) throws Exception {
        Path path = conversionService.cover(user(authentication), id);
        String type = Optional.ofNullable(Files.probeContentType(path)).orElse("image/jpeg");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.noCache()).body(new FileSystemResource(path));
    }

    @PostMapping("/{id}/convert")
    public BookConversionTaskDTO convert(Authentication authentication, @PathVariable Long id) {
        return conversionService.convert(user(authentication), id);
    }

    @GetMapping("/{id}/preview")
    public Map<String, Object> preview(Authentication authentication, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int chapter) {
        return conversionService.preview(user(authentication), id, chapter);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(Authentication authentication, @PathVariable Long id) {
        User user = user(authentication);
        BookConversionTaskDTO task = conversionService.get(user, id);
        Path path = conversionService.result(user, id);
        String ascii = task.getOutputFilename().replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = java.net.URLEncoder.encode(task.getOutputFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/epub+zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
                .contentLength(task.getOutputSize()).body(new FileSystemResource(path));
    }

    @PostMapping("/{id}/attach/{bookId}")
    public BookVersionDTO attach(Authentication authentication, @PathVariable Long id, @PathVariable Long bookId) {
        return conversionService.attach(user(authentication), id, bookId);
    }

    @PostMapping("/{id}/create-book")
    public BookDTO createBook(Authentication authentication, @PathVariable Long id) {
        return conversionService.createBook(user(authentication), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        conversionService.delete(user(authentication), id);
    }

    private User user(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
