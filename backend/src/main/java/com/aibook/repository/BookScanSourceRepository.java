package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookScanSource;
import com.aibook.model.entity.ScanDirectory;
import com.aibook.model.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookScanSourceRepository extends JpaRepository<BookScanSource, Long> {

    boolean existsByBookAndScanDirectory(Book book, ScanDirectory scanDirectory);

    void deleteByScanDirectory(ScanDirectory scanDirectory);

    List<BookScanSource> findByBook(Book book);

    List<BookScanSource> findByUser(User user);

    @Query("SELECT source.book.id AS bookId, source.scanDirectory.id AS scanDirectoryId "
            + "FROM BookScanSource source WHERE source.book.id IN :bookIds")
    List<BookScanSourceKeyProjection> findKeysByBookIds(
            @Param("bookIds") List<Long> bookIds);
}
