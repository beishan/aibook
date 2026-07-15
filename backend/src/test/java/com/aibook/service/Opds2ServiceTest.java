package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class Opds2ServiceTest {

    @Test
    void searchPaginationKeepsEncodedQueryParameter() {
        PageImpl<Book> page = new PageImpl<>(List.of(), PageRequest.of(1, 50), 120);
        Opds2Service service = new Opds2Service(repository(page));

        Map<String, Object> feed = service.searchBooks(user(), "三体 & 文明", 1);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> links = (List<Map<String, String>>) feed.get("links");
        assertThat(links).extracting(link -> link.get("href")).containsExactly(
                "/opds/v2/search?query=%E4%B8%89%E4%BD%93+%26+%E6%96%87%E6%98%8E&page=1",
                "/opds/v2",
                "/opds/v2/search?query=%E4%B8%89%E4%BD%93+%26+%E6%96%87%E6%98%8E&page=2",
                "/opds/v2/search?query=%E4%B8%89%E4%BD%93+%26+%E6%96%87%E6%98%8E&page=0"
        );
    }

    private User user() {
        return User.builder().id(1L).username("reader").build();
    }

    private BookRepository repository(PageImpl<Book> searchPage) {
        return (BookRepository) Proxy.newProxyInstance(
                BookRepository.class.getClassLoader(),
                new Class<?>[] {BookRepository.class},
                (proxy, method, args) -> {
                    if ("searchByKeyword".equals(method.getName())) {
                        return searchPage;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
