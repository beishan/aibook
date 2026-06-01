package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.Category;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CategoryRepository;
import com.aibook.util.MimeTypeUtil;
import com.aibook.util.WebDavXmlResponseBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WebDAV 服务
 * 实现 WebDAV 协议，支持 KOReader 等客户端的文件同步
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebDavService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 列出目录内容（返回 WebDAV 资源列表）
     */
    public List<WebDavXmlResponseBuilder.WebDavResource> listDirectory(User user, String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return listRoot(user);
        }

        // 去掉开头的 /
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = cleanPath.split("/");

        return switch (segments[0]) {
            case "formats" -> listFormats(user, segments);
            case "categories" -> listCategories(user, segments);
            case "tags" -> listTags(user, segments);
            default -> listRoot(user);
        };
    }

    /**
     * 列出根目录
     */
    private List<WebDavXmlResponseBuilder.WebDavResource> listRoot(User user) {
        List<WebDavXmlResponseBuilder.WebDavResource> resources = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 格式目录
        resources.add(new WebDavXmlResponseBuilder.WebDavResource(
                "formats", true, null, null, now, null));

        // 分类目录
        resources.add(new WebDavXmlResponseBuilder.WebDavResource(
                "categories", true, null, null, now, null));

        // 标签目录
        resources.add(new WebDavXmlResponseBuilder.WebDavResource(
                "tags", true, null, null, now, null));

        return resources;
    }

    /**
     * 列出格式子目录
     */
    private List<WebDavXmlResponseBuilder.WebDavResource> listFormats(User user, String[] segments) {
        LocalDateTime now = LocalDateTime.now();

        // /formats -> 列出所有格式
        if (segments.length == 1) {
            String[] formats = {"epub", "pdf", "txt", "mobi", "azw3", "docx", "html", "md"};
            return Arrays.stream(formats)
                    .map(f -> new WebDavXmlResponseBuilder.WebDavResource(f, true, null, null, now, null))
                    .toList();
        }

        // /formats/epub -> 列出该格式的书籍
        String format = segments[1];
        List<Book> books = bookRepository.findByUserAndFormat(user, format, PageRequest.of(0, 1000)).getContent();
        return books.stream()
                .map(this::bookToResource)
                .toList();
    }

    /**
     * 列出分类子目录
     */
    private List<WebDavXmlResponseBuilder.WebDavResource> listCategories(User user, String[] segments) {
        LocalDateTime now = LocalDateTime.now();

        // /categories -> 列出所有分类
        if (segments.length == 1) {
            List<Category> categories = categoryRepository.findByUser(user);
            return categories.stream()
                    .map(c -> new WebDavXmlResponseBuilder.WebDavResource(
                            c.getName(), true, null, null,
                            c.getCreatedAt() != null ? c.getCreatedAt() : now, null))
                    .toList();
        }

        // /categories/fiction -> 列出该分类的书籍（通过分类名查找）
        String categoryName = segments[1];
        List<Category> categories = categoryRepository.findByUser(user);
        Optional<Category> matchedCategory = categories.stream()
                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                .findFirst();

        if (matchedCategory.isEmpty()) {
            return List.of();
        }

        List<Book> books = bookRepository.findByUserAndCategoryId(user, matchedCategory.get().getId(),
                PageRequest.of(0, 1000)).getContent();
        return books.stream()
                .map(this::bookToResource)
                .toList();
    }

    /**
     * 列出标签子目录
     */
    private List<WebDavXmlResponseBuilder.WebDavResource> listTags(User user, String[] segments) {
        LocalDateTime now = LocalDateTime.now();

        // 简化实现：返回空列表
        // 完整实现需要 TagRepository 查询
        if (segments.length == 1) {
            return List.of();
        }

        return List.of();
    }

    /**
     * 获取资源信息
     */
    public WebDavXmlResponseBuilder.WebDavResource getResource(User user, String path) {
        String filename = Paths.get(path).getFileName().toString();

        // 尝试通过文件名查找书籍
        Optional<Book> bookOpt = bookRepository.findByUserAndFilename(user, filename);
        if (bookOpt.isPresent()) {
            return bookToResource(bookOpt.get());
        }

        // 检查是否是目录路径
        if (path.endsWith("/") || !filename.contains(".")) {
            return new WebDavXmlResponseBuilder.WebDavResource(
                    filename, true, null, null, LocalDateTime.now(), null);
        }

        return null;
    }

    /**
     * 获取书籍文件路径
     */
    public Path getBookFilePath(User user, String path) {
        String filename = Paths.get(path).getFileName().toString();

        Optional<Book> bookOpt = bookRepository.findByUserAndFilename(user, filename);
        if (bookOpt.isPresent()) {
            return Paths.get(bookOpt.get().getFilePath());
        }

        return null;
    }

    /**
     * 检查资源是否存在
     */
    public boolean resourceExists(User user, String path) {
        return getResource(user, path) != null;
    }

    /**
     * 检查是否为集合（目录）
     */
    public boolean isCollection(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return true;
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = cleanPath.split("/");
        if (segments.length == 1) {
            return switch (segments[0]) {
                case "formats", "categories", "tags" -> true;
                default -> false;
            };
        }
        if (segments.length == 2 && "formats".equals(segments[0])) {
            return true; // 格式子目录
        }
        return false;
    }

    /**
     * 创建集合（MKCOL）
     */
    public boolean createCollection(User user, String path) {
        // WebDAV MKCOL for virtual directories - 虚拟目录无法真正创建
        // 返回 false 表示不支持
        log.info("MKCOL not supported for virtual directory: {}", path);
        return false;
    }

    /**
     * 删除资源
     */
    public boolean deleteResource(User user, String path) {
        String filename = Paths.get(path).getFileName().toString();
        Optional<Book> bookOpt = bookRepository.findByUserAndFilename(user, filename);
        if (bookOpt.isPresent()) {
            bookRepository.delete(bookOpt.get());
            return true;
        }
        return false;
    }

    /**
     * Book -> WebDavResource
     */
    private WebDavXmlResponseBuilder.WebDavResource bookToResource(Book book) {
        String filename = book.getTitle() + "." + book.getFormat();
        String contentType = MimeTypeUtil.getContentType(book.getFormat());
        LocalDateTime lastModified = book.getUpdatedAt() != null ? book.getUpdatedAt() : book.getCreatedAt();
        String etag = "\"" + (book.getFileHash() != null ? book.getFileHash() : book.getId()) + "\"";

        return new WebDavXmlResponseBuilder.WebDavResource(
                filename,
                false,
                book.getFileSize(),
                contentType,
                lastModified,
                etag
        );
    }
}
