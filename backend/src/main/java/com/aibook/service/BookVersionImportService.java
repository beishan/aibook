package com.aibook.service;

import com.aibook.dto.BookVersionCandidateDTO;
import com.aibook.dto.BookVersionCandidatePageDTO;
import com.aibook.dto.BookVersionDTO;
import com.aibook.dto.BookVersionImportRequest;
import com.aibook.dto.BookVersionImportRequest.SourceHandling;
import com.aibook.dto.BookVersionImportRequest.SourceSelection;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookVersionRepository;
import com.aibook.repository.LibraryChapterRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** 在同一账户的书籍之间复制或合并文件版本。 */
@Service
@RequiredArgsConstructor
public class BookVersionImportService {

    private static final String IMPORT_SOURCE_TYPE = "LIBRARY_VERSION_IMPORT";

    private final BookRepository bookRepository;
    private final BookVersionRepository versionRepository;
    private final LibraryChapterRepository chapterRepository;
    private final BookVersionService versionService;
    private final BookVersionAggregationService aggregationService;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public BookVersionCandidatePageDTO candidates(
            Book target, User user, int page, int size, String keyword) {
        Page<Book> result = bookRepository.findVersionImportCandidates(
                user,
                target.getId(),
                target.getTitle() == null ? "" : target.getTitle(),
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))));
        List<Long> ids = result.getContent().stream().map(Book::getId).toList();
        Map<Long, Long> counts = (ids.isEmpty()
                ? List.<BookVersionRepository.BookVersionCount>of()
                : versionRepository.countByBookIds(ids)).stream()
                .collect(Collectors.toMap(
                        BookVersionRepository.BookVersionCount::getBookId,
                        BookVersionRepository.BookVersionCount::getVersionCount));
        String targetTitle = normalizeTitle(target.getTitle());
        List<BookVersionCandidateDTO> content = result.getContent().stream()
                .map(book -> new BookVersionCandidateDTO(
                        book.getId(),
                        book.getTitle(),
                        book.getAuthor(),
                        book.getCoverUrl(),
                        book.getFormat(),
                        Math.max(1L, counts.getOrDefault(book.getId(), 0L)),
                        normalizeTitle(book.getTitle()).equals(targetTitle)))
                .toList();
        return new BookVersionCandidatePageDTO(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize());
    }

    @Transactional
    public List<BookVersionDTO> importVersions(
            Book target, User user, BookVersionImportRequest request) {
        if (request == null || request.sources() == null || request.sources().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要导入的版本");
        }
        SourceHandling handling = request.sourceHandling() == null
                ? SourceHandling.KEEP
                : request.sourceHandling();
        List<Long> sourceIds = request.sources().stream()
                .map(SourceSelection::bookId)
                .filter(id -> id != null && !id.equals(target.getId()))
                .distinct()
                .toList();
        if (sourceIds.size() != request.sources().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备选书籍选择无效或重复");
        }
        Map<Long, Book> sources = bookRepository.findByIdInAndUser(sourceIds, user).stream()
                .collect(Collectors.toMap(Book::getId, book -> book));
        if (sources.size() != sourceIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分备选书籍不存在");
        }

        for (SourceSelection selection : request.sources()) {
            Book source = sources.get(selection.bookId());
            versionService.ensurePrimaryVersion(source);
            List<BookVersion> available = versionRepository
                    .findByBookOrderByPrimaryVersionDescCreatedAtAsc(source);
            Set<Long> selectedIds = selection.versionIds() == null
                    ? Set.of()
                    : new HashSet<>(selection.versionIds());
            if (selectedIds.isEmpty()
                    || available.stream().noneMatch(version -> selectedIds.contains(version.getId()))
                    || selectedIds.stream().anyMatch(id -> available.stream()
                            .noneMatch(version -> version.getId().equals(id)))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "包含无效的版本选择");
            }
            if (handling == SourceHandling.MERGE && selectedIds.size() != available.size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "合并书籍时必须选择该书籍的全部版本");
            }
        }

        versionService.ensurePrimaryVersion(target);
        if (handling == SourceHandling.MERGE) {
            for (SourceSelection selection : request.sources()) {
                aggregationService.aggregatePairInCurrentTransaction(
                        target.getId(), selection.bookId(), user);
            }
        } else {
            for (SourceSelection selection : request.sources()) {
                Book source = sources.get(selection.bookId());
                Set<Long> selectedIds = new HashSet<>(selection.versionIds());
                versionRepository.findByBookOrderByPrimaryVersionDescCreatedAtAsc(source).stream()
                        .filter(version -> selectedIds.contains(version.getId()))
                        .forEach(version -> copyVersion(target, version));
            }
        }
        return versionService.getVersions(target);
    }

    private void copyVersion(Book target, BookVersion source) {
        String sourceId = String.valueOf(source.getId());
        if (versionRepository.existsByBookAndSourceTypeAndSourceId(
                target, IMPORT_SOURCE_TYPE, sourceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选版本已经导入当前书籍");
        }
        String copiedPath = source.getFilePath();
        Path createdFile = null;
        if (!"structured".equalsIgnoreCase(source.getFormat())) {
            Path sourcePath = Paths.get(source.getFilePath());
            if (!Files.isRegularFile(sourcePath)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "源版本文件不存在");
            }
            String extension = source.getFormat() == null
                    ? "bin"
                    : source.getFormat().toLowerCase(Locale.ROOT);
            createdFile = Paths.get(uploadPath).resolve(UUID.randomUUID() + "." + extension);
            try {
                Files.createDirectories(createdFile.getParent());
                Files.copy(sourcePath, createdFile, StandardCopyOption.COPY_ATTRIBUTES);
                registerRollbackCleanup(createdFile);
                copiedPath = createdFile.toString();
            } catch (Exception exception) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "复制源版本文件失败", exception);
            }
        }
        try {
            BookVersion copied = versionRepository.save(BookVersion.builder()
                    .book(target)
                    .displayName(source.getDisplayName())
                    .format(source.getFormat())
                    .filePath(copiedPath)
                    .fileSize(source.getFileSize())
                    .fileHash(null)
                    .primaryVersion(false)
                    .chapterInfo(source.getChapterInfo())
                    .chapterCount(source.getChapterCount())
                    .sourceType(IMPORT_SOURCE_TYPE)
                    .sourceId(sourceId)
                    .sourceSite(source.getSourceSite())
                    .sourceUrl(source.getSourceUrl())
                    .build());
            if ("structured".equalsIgnoreCase(source.getFormat())) {
                List<LibraryChapter> chapters = chapterRepository
                        .findByBookVersionOrderByChapterIndexAsc(source).stream()
                        .map(chapter -> LibraryChapter.builder()
                                .bookVersion(copied)
                                .chapterKey(chapter.getChapterKey())
                                .chapterIndex(chapter.getChapterIndex())
                                .title(chapter.getTitle())
                                .content(chapter.getContent())
                                .contentHash(chapter.getContentHash())
                                .wordCount(chapter.getWordCount())
                                .build())
                        .toList();
                chapterRepository.saveAll(chapters);
            }
        } catch (RuntimeException exception) {
            if (createdFile != null) {
                try {
                    Files.deleteIfExists(createdFile);
                } catch (Exception ignored) {
                    // 数据库异常优先返回；遗留文件可由存储清理任务处理。
                }
            }
            throw exception;
        }
    }

    private String normalizeTitle(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void registerRollbackCleanup(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) return;
                        try {
                            Files.deleteIfExists(file);
                        } catch (Exception ignored) {
                            // 回滚已经完成，文件清理由存储维护任务兜底。
                        }
                    }
                });
    }
}
