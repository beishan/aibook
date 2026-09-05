package com.aibook.service;

import com.aibook.dto.SeriesBookDTO;
import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.repository.BookSeriesSummary;
import com.aibook.util.BookSeriesMetadata;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookSeriesService {
    private final BookRepository bookRepository;

    public List<BookSeriesSummary> list(User user) {
        return bookRepository.findSeriesSummaries(user, Book.ReadingStatus.FINISHED);
    }

    public List<SeriesBookDTO> books(User user, String name) {
        String normalized = BookSeriesMetadata.normalizeName(name);
        if (normalized.isEmpty()) return List.of();
        return bookRepository.findSeriesBooks(user, normalized).stream()
                .sorted(Comparator.comparing(Book::getSeriesIndex,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Book::getId))
                .map(book -> new SeriesBookDTO(book.getId(), book.getTitle(), book.getAuthor(),
                        book.getCoverUrl(), book.getFormat(), book.getSeriesIndex(), book.getReadingStatus().name()))
                .toList();
    }
}
