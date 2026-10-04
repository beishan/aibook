package com.aibook.service.crawler;

import com.aibook.dto.crawler.CrawlerCategoryCleanupDtos.*;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.CrawlerBookRepository;
import com.aibook.repository.projections.CrawlerCategoryCleanupCandidate;
import com.aibook.util.CrawlerCategoryPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlerCategoryCleanupService {
    private final CrawlerBookRepository crawlerBooks;
    private final BookRepository libraryBooks;

    @Transactional(readOnly = true)
    public Preview preview(User user, Request request) {
        List<Item> samples = new ArrayList<>();
        int[] counts = {0, 0};
        visitMatches(user, request, book -> {
            counts[0]++;
            boolean matchingLibrary = matchesLibrary(book);
            if (matchingLibrary) counts[1]++;
            if (samples.size() < 100) {
                samples.add(new Item(book.getId(), book.getBookName(), book.getCategory(),
                        book.getSiteName(), matchingLibrary));
            }
        });
        return new Preview(counts[0], counts[1], samples);
    }

    @Transactional
    public Result cleanup(User user, Request request) {
        int[] counts = {0, 0, 0};
        visitMatches(user, request, book -> {
            if (crawlerBooks.clearCategoryIfUnchanged(book.getId(), user, book.getCategory()) != 1) {
                counts[2]++;
                return;
            }
            counts[0]++;
            if (request.syncLibrary() && matchesLibrary(book)) {
                counts[1] += libraryBooks.clearCrawlerCategoryIfUnchanged(
                        book.getLibraryBookId(), user, book.getLibraryCategoryId());
            }
        });
        return new Result(counts[0], counts[1], counts[2]);
    }

    private void visitMatches(User user, Request request, Consumer<CrawlerCategoryCleanupCandidate> visitor) {
        Set<String> names = new HashSet<>();
        if (request.categoryNames() != null) {
            request.categoryNames().stream().map(CrawlerCategoryPolicy::normalize)
                    .filter(name -> !name.isEmpty()).forEach(names::add);
        }
        long afterId = 0;
        while (true) {
            var page = crawlerBooks.findCategoryCleanupCandidates(
                    user, request.siteId(), afterId, PageRequest.of(0, 500));
            if (page.isEmpty()) return;
            for (var book : page) {
                afterId = book.getId();
                if (CrawlerCategoryPolicy.isInvalidCategory(
                        book.getBookName(), book.getAuthor(), book.getCategory())
                        || names.contains(CrawlerCategoryPolicy.normalize(book.getCategory()))) {
                    visitor.accept(book);
                }
            }
        }
    }

    private boolean matchesLibrary(CrawlerCategoryCleanupCandidate book) {
        return book.getLibraryBookId() != null && book.getLibraryCategoryId() != null
                && CrawlerCategoryPolicy.normalize(book.getCategory()).equals(
                        CrawlerCategoryPolicy.normalize(book.getLibraryCategoryName()));
    }
}
