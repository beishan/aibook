package com.aibook.controller;

import com.aibook.dto.FontDirectoryNodeDTO;
import com.aibook.dto.FontScanDirectoryDTO;
import com.aibook.dto.FontScanDirectoryRequest;
import com.aibook.service.FontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/font-scan-directories")
@RequiredArgsConstructor
public class FontScanDirectoryController {

    private final FontService fontService;

    @GetMapping
    public ResponseEntity<List<FontScanDirectoryDTO>> list() {
        return ResponseEntity.ok(fontService.listDirectories());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<FontDirectoryNodeDTO>> tree(
            @RequestParam(required = false) String path) {
        return ResponseEntity.ok(fontService.browseDirectories(path));
    }

    @PostMapping
    public ResponseEntity<FontScanDirectoryDTO> add(
            @RequestBody FontScanDirectoryRequest request) {
        return ResponseEntity.ok(fontService.addDirectory(request.getPath()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fontService.deleteDirectory(id);
        return ResponseEntity.noContent().build();
    }
}
