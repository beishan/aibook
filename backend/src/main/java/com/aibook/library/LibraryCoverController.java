package com.aibook.library;

import com.aibook.service.UserService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/libraries/{id}/cover")
@RequiredArgsConstructor
public class LibraryCoverController {
    private final LibraryCoverService covers;
    private final UserService users;

    @GetMapping
    public ResponseEntity<FileSystemResource> image(Authentication authentication, @PathVariable long id) {
        var file = covers.image(users.findByUsername(authentication.getName()), id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(file));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(Authentication authentication, @PathVariable long id,
            @RequestParam long revision, @RequestParam("file") MultipartFile file) {
        return covers.upload(users.findByUsername(authentication.getName()), id, revision, file);
    }

    @DeleteMapping
    public Map<String, Object> remove(Authentication authentication, @PathVariable long id,
            @RequestParam long revision) {
        return covers.remove(users.findByUsername(authentication.getName()), id, revision);
    }
}
