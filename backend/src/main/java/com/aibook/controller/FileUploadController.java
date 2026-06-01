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
     * 上传书籍文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadBook(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) throws IOException {

        User user = userService.findByUsername(authentication.getName());

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // 确保上传目录存在
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // 保存文件
        Path filePath = uploadDir.resolve(uniqueFilename);
        file.transferTo(filePath.toFile());

        // 计算文件哈希
        String fileHash = calculateFileHash(filePath);

        // 检查是否已存在
        if (bookRepository.findByFileHash(fileHash).isPresent()) {
            Files.deleteIfExists(filePath);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "文件已存在"
            ));
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

        log.info("文件上传成功: {}", originalFilename);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "上传成功",
            "bookId", book.getId()
        ));
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
