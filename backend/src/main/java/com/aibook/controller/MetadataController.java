package com.aibook.controller;

import com.aibook.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 元数据控制器
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MetadataController {

    private final MetadataService metadataService;

    /**
     * 通过 ISBN 查询书籍元数据
     */
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<Map<String, Object>> searchByIsbn(@PathVariable String isbn) {
        Map<String, Object> metadata = metadataService.searchByIsbn(isbn);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }

    /**
     * 通过书名搜索书籍元数据
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchByTitle(
            @RequestParam String title,
            @RequestParam(required = false) String author) {
        Map<String, Object> metadata = metadataService.searchByTitle(title, author);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }
}
