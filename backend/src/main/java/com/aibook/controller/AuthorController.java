package com.aibook.controller;

import com.aibook.dto.AuthorDTO;
import com.aibook.dto.AuthorRequest;
import com.aibook.model.entity.User;
import com.aibook.service.AuthorService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户的书籍作者管理接口。 */
@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AuthorDTO>> getAuthors(Authentication authentication) {
        return ResponseEntity.ok(authorService.getAuthors(currentUser(authentication)));
    }

    @PostMapping
    public ResponseEntity<AuthorDTO> createAuthor(
            Authentication authentication,
            @Valid @RequestBody AuthorRequest request) {
        return ResponseEntity.ok(authorService.createAuthor(currentUser(authentication), request));
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }
}
