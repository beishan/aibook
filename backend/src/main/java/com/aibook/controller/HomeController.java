package com.aibook.controller;

import com.aibook.service.HomeService;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {
    private final HomeService homeService;
    private final UserService userService;

    @GetMapping
    public HomeService.Dashboard dashboard(Authentication authentication) {
        return homeService.dashboard(userService.findByUsername(authentication.getName()));
    }
}
