package com.aibook.controller;

import com.aibook.dto.SeriesBookDTO;
import com.aibook.repository.BookSeriesSummary;
import com.aibook.service.BookSeriesService;
import com.aibook.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class BookSeriesController {
    private final BookSeriesService seriesService;
    private final UserService userService;

    @GetMapping
    public List<BookSeriesSummary> list(Authentication authentication) {
        return seriesService.list(userService.findByUsername(authentication.getName()));
    }

    @GetMapping("/books")
    public List<SeriesBookDTO> books(Authentication authentication, @RequestParam String name) {
        return seriesService.books(userService.findByUsername(authentication.getName()), name);
    }
}
