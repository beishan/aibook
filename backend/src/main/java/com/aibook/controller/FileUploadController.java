package com.aibook.controller;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class FileUploadController {

    private final BookRepository bookRepository;
    private final UserService userService;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    /**
     * 上传书籍文件（支持多文件）
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadBook(
            Authentication authentication,
            @RequestParam("files") List<MultipartFile> files) throws IOException {

        User user = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        // 确保上传目录存在
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        for (MultipartFile file : files) {
            try {
                // 生成唯一文件名
                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String uniqueFilename = UUID.randomUUID().toString() + extension;

                // 保存文件
                Path filePath = uploadDir.resolve(uniqueFilename);
                file.transferTo(filePath.toFile());

                // 计算文件哈希
                String fileHash = calculateFileHash(filePath);

                // 检查是否已存在
                if (bookRepository.findByFileHash(fileHash).isPresent()) {
                    Files.deleteIfExists(filePath);
                    results.add(Map.of(
                        "filename", originalFilename,
                        "success", false,
                        "message", "文件已存在"
                    ));
                    failCount++;
                    continue;
                }

                // 提取书名（去掉扩展名）
                String title = originalFilename;
                if (title != null && title.contains(".")) {
                    title = title.substring(0, title.lastIndexOf("."));
                }

                // 创建书籍记录
                Book book = Book.builder()
                        .title(title)
                        .format(extension.substring(1).toLowerCase())
                        .filePath(filePath.toString())
                        .fileSize(file.getSize())
                        .fileHash(fileHash)
                        .user(user)
                        .build();

                bookRepository.save(book);
                successCount++;

                results.add(Map.of(
                    "filename", originalFilename,
                    "success", true,
                    "message", "上传成功",
                    "bookId", book.getId()
                ));

                log.info("文件上传成功: {}", originalFilename);
            } catch (Exception e) {
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);
                results.add(Map.of(
                    "filename", file.getOriginalFilename(),
                    "success", false,
                    "message", "上传失败: " + e.getMessage()
                ));
                failCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", successCount > 0);
        response.put("message", String.format("上传完成: 成功 %d 个, 失败 %d 个", successCount, failCount));
        response.put("results", results);
        response.put("totalCount", files.size());
        response.put("successCount", successCount);
        response.put("failCount", failCount);

        return ResponseEntity.ok(response);
    }

    /**
     * 计算文件哈希
     */
    private String calculateFileHash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算文件哈希失败", e);
        }
    }
}
