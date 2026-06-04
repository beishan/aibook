package com.aibook.controller;

import com.aibook.model.dto.DirectoryItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件浏览控制器
 * 提供服务器文件系统目录浏览功能
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
@Slf4j
public class FileBrowseController {

    /**
     * 允许访问的根目录白名单
     */
    private static final Set<String> ALLOWED_ROOTS = Set.of(
            "/",
            "/home",
            "/books",
            "/app",
            "/uploads",
            "/data",
            "/media",
            "/scanfolder"
    );

    /**
     * 禁止访问的目录黑名单
     */
    private static final Set<String> BLOCKED_PATHS = Set.of(
            "/etc",
            "/proc",
            "/sys",
            "/dev",
            "/boot",
            "/sbin",
            "/bin",
            "/usr/bin",
            "/usr/sbin"
    );

    /**
     * 浏览指定路径下的目录内容
     *
     * @param path 目录路径，默认为根目录
     * @return 目录项列表
     */
    @GetMapping("/browse")
    public ResponseEntity<List<DirectoryItem>> browse(
            @RequestParam(defaultValue = "/") String path) {

        log.info("浏览目录: {}", path);

        // 路径安全验证
        if (!isPathAllowed(path)) {
            log.warn("路径不允许访问: {}", path);
            return ResponseEntity.badRequest().body(List.of());
        }

        Path dirPath = Paths.get(path);

        // 检查路径是否存在
        if (!Files.exists(dirPath)) {
            log.warn("路径不存在: {}", path);
            return ResponseEntity.badRequest().body(List.of());
        }

        // 检查是否为目录
        if (!Files.isDirectory(dirPath)) {
            log.warn("路径不是目录: {}", path);
            return ResponseEntity.badRequest().body(List.of());
        }

        try {
            List<DirectoryItem> items = Files.list(dirPath)
                    .filter(p -> !p.getFileName().toString().startsWith(".")) // 过滤隐藏文件
                    .sorted((a, b) -> {
                        // 目录优先，然后按名称排序
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) {
                            return aDir ? -1 : 1;
                        }
                        return a.getFileName().toString().compareToIgnoreCase(
                                b.getFileName().toString());
                    })
                    .map(p -> {
                        try {
                            boolean isDir = Files.isDirectory(p);
                            long size = isDir ? 0 : Files.size(p);
                            // 检查子目录是否可访问
                            boolean accessible = isDir ? isPathAllowed(p.toString()) : false;
                            return DirectoryItem.builder()
                                    .name(p.getFileName().toString())
                                    .path(p.toString())
                                    .isDirectory(isDir)
                                    .size(size)
                                    .type(isDir ? "目录" : getFileType(p.toString()))
                                    .accessible(accessible)
                                    .build();
                        } catch (IOException e) {
                            log.warn("无法读取文件信息: {}", p, e);
                            return DirectoryItem.builder()
                                    .name(p.getFileName().toString())
                                    .path(p.toString())
                                    .isDirectory(false)
                                    .size(0)
                                    .type("未知")
                                    .accessible(false)
                                    .build();
                        }
                    })
                    .collect(Collectors.toList());

            log.info("找到 {} 个目录项", items.size());
            return ResponseEntity.ok(items);
        } catch (IOException e) {
            log.error("无法浏览目录: {}", path, e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    /**
     * 获取常用目录列表
     */
    @GetMapping("/quick-dirs")
    public ResponseEntity<List<DirectoryItem>> getQuickDirectories() {
        List<DirectoryItem> quickDirs = new ArrayList<>();

        // 添加系统根目录
        quickDirs.add(DirectoryItem.builder()
                .name("/")
                .path("/")
                .isDirectory(true)
                .type("根目录")
                .build());

        // 添加常用目录
        String[] commonPaths = {"/home", "/books", "/app", "/uploads", "/data", "/media", "/scanfolder"};
        for (String path : commonPaths) {
            Path dirPath = Paths.get(path);
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                quickDirs.add(DirectoryItem.builder()
                        .name(path)
                        .path(path)
                        .isDirectory(true)
                        .type("常用目录")
                        .build());
            }
        }

        // 添加用户主目录
        String userHome = System.getProperty("user.home");
        if (userHome != null && Files.exists(Paths.get(userHome))) {
            quickDirs.add(DirectoryItem.builder()
                    .name("用户主目录")
                    .path(userHome)
                    .isDirectory(true)
                    .type("用户目录")
                    .build());
        }

        return ResponseEntity.ok(quickDirs);
    }

    /**
     * 验证路径是否允许访问
     */
    private boolean isPathAllowed(String path) {
        // 规范化路径
        try {
            path = Paths.get(path).normalize().toString();
        } catch (Exception e) {
            log.warn("路径规范化失败: {}", path, e);
            return false;
        }

        // 首先检查黑名单 - 这是最重要的安全检查
        for (String blocked : BLOCKED_PATHS) {
            if (path.equals(blocked) || path.startsWith(blocked + "/")) {
                log.warn("禁止访问路径: {}", path);
                return false;
            }
        }

        // 根目录允许访问
        if (path.equals("/")) {
            return true;
        }

        // 检查是否在允许的根目录下
        for (String root : ALLOWED_ROOTS) {
            if (root.equals("/")) continue; // 跳过根目录
            if (path.equals(root) || path.startsWith(root + "/")) {
                return true;
            }
        }

        log.warn("路径不在允许范围内: {}", path);
        return false;
    }

    /**
     * 根据文件扩展名获取文件类型
     */
    private String getFileType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".epub")) return "EPUB电子书";
        if (lower.endsWith(".pdf")) return "PDF文档";
        if (lower.endsWith(".txt")) return "文本文件";
        if (lower.endsWith(".mobi")) return "MOBI电子书";
        if (lower.endsWith(".azw3")) return "AZW3电子书";
        if (lower.endsWith(".docx")) return "Word文档";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML文件";
        if (lower.endsWith(".md")) return "Markdown文件";
        if (lower.endsWith(".cbz") || lower.endsWith(".cbr")) return "漫画文件";
        return "文件";
    }
}
