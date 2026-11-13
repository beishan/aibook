package com.aibook.controller;

import com.aibook.dto.BookDTO;
import com.aibook.dto.BookMembershipRequest;
import com.aibook.service.BookMembershipService;
import com.aibook.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookMembershipController {

    private final BookMembershipService membershipService;
    private final UserService userService;

    @PutMapping("/api/books/batch/membership")
    public List<BookDTO> add(Authentication authentication,
            @Valid @RequestBody BookMembershipRequest request) {
        return membershipService.add(request, userService.findByUsername(authentication.getName()));
    }
}
