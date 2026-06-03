package com.aibook.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件浏览器控制器
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileBrowserController {

    /**
     * 浏览目录
     */
    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browseDirectory(
            @RequestParam(defaultValue = "/") String path) {

        File dir = new File(path);

        // 如果路径不存在，返回根目录列表
        if (!dir.exists() || !dir.isDirectory()) {
            return ResponseEntity.ok(getRootDirectories());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("path", dir.getAbsolutePath());
        result.put("parent", dir.getParent());

        // 获取子目录列表
        File[] files = dir.listFiles();
        if (files != null) {
            List<Map<String, Object>> directories = Arrays.stream(files)
                    .filter(File::isDirectory)
                    .filter(f -> !f.isHidden()) // 隐藏文件
                    .sorted(Comparator.comparing(File::getName))
                    .map(this::getFileInfo)
                    .collect(Collectors.toList());
            result.put("directories", directories);
        } else {
            result.put("directories", Collections.emptyList());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取根目录列表
     */
    private Map<String, Object> getRootDirectories() {
        Map<String, Object> result = new HashMap<>();
        result.put("path", "");
        result.put("parent", "");

        List<Map<String, Object>> directories = new ArrayList<>();

        // Windows 系统获取盘符
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                directories.add(getFileInfo(root));
            }
        }

        result.put("directories", directories);
        return result;
    }

    /**
     * 获取文件信息
     */
    private Map<String, Object> getFileInfo(File file) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", file.getName());
        info.put("path", file.getAbsolutePath());
        info.put("isDirectory", file.isDirectory());
        info.put("readable", file.canRead());
        return info;
    }
}
