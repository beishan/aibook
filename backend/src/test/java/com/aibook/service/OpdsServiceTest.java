package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class OpdsServiceTest {

    @Test
    void rootCatalogIncludesSelfStartAndSearchLinks() {
        OpdsService service = new OpdsService(repository());

        String xml = service.getRootCatalog(user());

        assertThat(xml).contains("rel=\"self\"");
        assertThat(xml).contains("href=\"/opds\"");
        assertThat(xml).contains("rel=\"start\"");
        assertThat(xml).contains("rel=\"search\"");
        assertThat(xml).contains("href=\"/opds/search.xml\"");
        assertThat(xml).contains("rel=\"subsection\"");
    }

    @Test
    void booksFeedEscapesMetadataAndIncludesAcquisitionAndCoverLinks() {
        Book book = book("三体 & 黑暗森林", "刘<慈欣>", "epub");
        book.setDescription("文明 > 危机");
        book.setCoverUrl("/api/covers/1.jpg");
        BookRepository bookRepository = repository(new PageImpl<>(List.of(book), PageRequest.of(0, 50), 80), null);
        OpdsService service = new OpdsService(bookRepository);

        String xml = service.getBooksCatalog(user(), 0);

        assertThat(xml).contains("三体 &amp; 黑暗森林");
        assertThat(xml).contains("刘&lt;慈欣&gt;");
        assertThat(xml).contains("文明 &gt; 危机");
        assertThat(xml).contains("rel=\"self\"");
        assertThat(xml).contains("rel=\"next\"");
        assertThat(xml).contains("type=\"application/epub+zip\"");
        assertThat(xml).contains("rel=\"http://opds-spec.org/acquisition/open-access\"");
        assertThat(xml).contains("rel=\"http://opds-spec.org/image\"");
        assertThat(xml).contains("rel=\"http://opds-spec.org/image/thumbnail\"");
    }

    @Test
    void searchPaginationKeepsQueryParameter() {
        BookRepository bookRepository = repository(
            null,
            new PageImpl<>(List.of(book("三体", "刘慈欣", "epub")), PageRequest.of(1, 50), 120)
        );
        OpdsService service = new OpdsService(bookRepository);

        String xml = service.searchBooks(user(), "三体", 1);

        assertThat(xml).contains("href=\"/opds/search?query=%E4%B8%89%E4%BD%93&amp;page=1\"");
        assertThat(xml).contains("href=\"/opds/search?query=%E4%B8%89%E4%BD%93&amp;page=2\"");
        assertThat(xml).contains("href=\"/opds/search?query=%E4%B8%89%E4%BD%93&amp;page=0\"");
    }

    @Test
    void searchDescriptionExposesOpenSearchTemplate() {
        OpdsService service = new OpdsService(repository());

        String xml = service.getSearchDescription();

        assertThat(xml).contains("<OpenSearchDescription");
        assertThat(xml).contains("template=\"/opds/search?query={searchTerms}\"");
        assertThat(xml).contains("application/atom+xml;profile=opds-catalog");
    }

    private User user() {
        return User.builder().id(1L).username("reader").build();
    }

    private Book book(String title, String author, String format) {
        return Book.builder()
            .id(10L)
            .title(title)
            .author(author)
            .format(format)
            .filePath("/tmp/book." + format)
            .user(user())
            .createdAt(LocalDateTime.of(2026, 7, 7, 12, 0))
            .updatedAt(LocalDateTime.of(2026, 7, 7, 12, 30))
            .build();
    }

    private BookRepository repository() {
        return repository(new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 50), 0), null);
    }

    private BookRepository repository(PageImpl<Book> booksPage, PageImpl<Book> searchPage) {
        return (BookRepository) Proxy.newProxyInstance(
            BookRepository.class.getClassLoader(),
            new Class<?>[] {BookRepository.class},
            (proxy, method, args) -> {
                if ("findByUser".equals(method.getName())) {
                    return booksPage;
                }
                if ("searchByKeyword".equals(method.getName())) {
                    return searchPage;
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
