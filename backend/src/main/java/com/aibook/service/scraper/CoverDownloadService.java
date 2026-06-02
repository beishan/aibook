package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 封面下载服务
 * 将远程封面图片下载到本地存储
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoverDownloadService {

    private final BookRepository bookRepository;
    private final RestTemplate restTemplate;

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.cover.dir:covers}")
    private String coverDir;

    /**
     * 下载书籍封面
     */
    @Transactional
    public Book downloadCover(Book book) {
        if (book.getCoverUrl() == null || book.getCoverUrl().isBlank()) {
            log.debug("书籍没有封面URL: {}", book.getTitle());
            return book;
        }

        // 如果已经是本地路径，跳过
        if (book.getCoverUrl().startsWith("/") || book.getCoverUrl().startsWith("covers/")) {
            log.debug("封面已是本地路径: {}", book.getTitle());
            return book;
        }

        try {
            log.info("下载封面: {} - {}", book.getTitle(), book.getCoverUrl());

            // 创建封面目录
            Path coverPath = Paths.get(uploadDir, coverDir);
            Files.createDirectories(coverPath);

            // 生成唯一文件名
            String extension = getFileExtension(book.getCoverUrl());
            String fileName = UUID.randomUUID() + extension;
            String filePath = coverPath.resolve(fileName).toString();

            // 下载图片
            byte[] imageBytes = restTemplate.getForObject(book.getCoverUrl(), byte[].class);
            if (imageBytes != null) {
                Files.write(Paths.get(filePath), imageBytes);
            } else {
                log.warn("封面下载返回空内容: {}", book.getCoverUrl());
                return book;
            }

            // 更新书籍封面路径
            String relativePath = coverDir + "/" + fileName;
            book.setCoverUrl(relativePath);
            bookRepository.save(book);

            log.info("封面下载成功: {} -> {}", book.getTitle(), relativePath);
            return book;
        } catch (Exception e) {
            log.error("封面下载失败: {} - {}", book.getTitle(), book.getCoverUrl(), e);
            return book;
        }
    }

    /**
     * 批量下载封面
     */
    @Transactional
    public int downloadMissingCovers() {
        var books = bookRepository.findByCoverUrlIsNullOrCoverUrlStartingWith("http");
        int count = 0;

        for (Book book : books) {
            try {
                downloadCover(book);
                count++;
            } catch (Exception e) {
                log.error("处理封面失败: {}", book.getTitle(), e);
            }
        }

        log.info("封面下载完成: {} 本", count);
        return count;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String url) {
        if (url == null) return ".jpg";

        // 移除查询参数
        String cleanUrl = url.split("\\?")[0];

        if (cleanUrl.endsWith(".png")) return ".png";
        if (cleanUrl.endsWith(".gif")) return ".gif";
        if (cleanUrl.endsWith(".webp")) return ".webp";
        return ".jpg"; // 默认
    }
}
