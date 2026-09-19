package com.aibook.service;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.BookVersion;
import com.aibook.model.entity.LibraryChapter;
import com.aibook.repository.LibraryChapterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuredPublicationServiceTest {

    @Test
    void exposesStructuredSnapshotAsProcessedTextAndManifest() throws Exception {
        LibraryChapterRepository repository = mock(LibraryChapterRepository.class);
        Book book = Book.builder().id(7L).title("测试书").author("作者").build();
        BookVersion version = BookVersion.builder().id(9L).book(book)
                .format("structured").build();
        LibraryChapter first = LibraryChapter.builder().id(11L).bookVersion(version)
                .chapterKey("c1").chapterIndex(0).title("同名章节")
                .content("第一段\n\n第二段").contentHash("h1").build();
        LibraryChapter second = LibraryChapter.builder().id(12L).bookVersion(version)
                .chapterKey("c2").chapterIndex(1).title("同名章节")
                .content("第三段\n第四段").contentHash("h2").build();
        when(repository.findByBookVersionOrderByChapterIndexAsc(version))
                .thenReturn(List.of(first, second));
        ObjectMapper objectMapper = new ObjectMapper();
        StructuredPublicationService service = new StructuredPublicationService(repository, objectMapper);

        var content = service.processedContent(version);
        var chapterInfo = objectMapper.readTree(content.get("chapterInfo"));
        var manifest = service.manifest(book, version);

        assertThat(content.get("text")).contains("同名章节", "第一段", "第三段");
        assertThat(content.get("text")).contains("第一段\n\n第二段", "第三段\n\n第四段");
        assertThat(content.get("text")).doesNotContain("第三段第四段");
        assertThat(chapterInfo).hasSize(2);
        assertThat(chapterInfo.get(0).get("key").asText()).isEqualTo("c1");
        assertThat((List<?>) manifest.get("readingOrder")).hasSize(2);
    }
}
