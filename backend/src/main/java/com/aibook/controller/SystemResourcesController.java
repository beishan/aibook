package com.aibook.controller;

import com.aibook.dto.SystemResourcesDTO;
import com.aibook.service.SystemResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/resources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemResourcesController {

    private final SystemResourceService systemResourceService;

    @GetMapping
    public ResponseEntity<SystemResourcesDTO> resources() {
        return ResponseEntity.ok(systemResourceService.getResources());
    }
}
