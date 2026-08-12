package com.aibook.controller;

import com.aibook.dto.RandomBookCoverDTO;
import com.aibook.model.entity.User;
import com.aibook.service.RandomBookCoverService;
import com.aibook.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/random-book-covers")
@RequiredArgsConstructor
public class RandomBookCoverController {

    private final RandomBookCoverService coverService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<RandomBookCoverDTO>> list(Authentication authentication) {
        return ResponseEntity.ok(coverService.list(currentUser(authentication)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<RandomBookCoverDTO>> upload(
            Authentication authentication,
            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.ok(coverService.upload(currentUser(authentication), files));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long id) {
        coverService.delete(currentUser(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
