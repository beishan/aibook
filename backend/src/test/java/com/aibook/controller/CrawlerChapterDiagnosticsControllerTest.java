package com.aibook.controller;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.CrawlerChapter;
import com.aibook.model.entity.User;
import com.aibook.repository.CrawlerChapterRepository;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerManagementService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerChapterDiagnosticsControllerTest {
    @Mock private UserService userService;
    @Mock private CrawlerManagementService managementService;
    @Mock private CrawlerChapterRepository chapterRepository;
    @Mock private Authentication authentication;
    @InjectMocks private CrawlerController controller;

    private CrawlerChapter chapter;

    @BeforeEach
    void setup() {
        User user = User.builder().id(1L).build();
        CrawlerBook book = CrawlerBook.builder().id(2L).build();
        chapter = CrawlerChapter.builder().id(3L).crawlerBook(book).build();
        when(authentication.getName()).thenReturn("owner");
        when(userService.findByUsername("owner")).thenReturn(user);
        when(managementService.ownedBook(user, 2L)).thenReturn(book);
        when(chapterRepository.findById(3L)).thenReturn(Optional.of(chapter));
    }

    @Test
    void downloadsUnmodifiedHtmlAsAttachmentWithoutInliningItInChapterDetails() {
        String html = "<!doctype html><html><body>错误页面<script>alert(1)</script></body></html>";
        chapter.setFailedResponseHtml(html);
        var response = controller.downloadFailedResponseHtml(authentication, 2L, 3L);

        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo(html);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment", "chapter-3-failed-response.html");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var detail = controller.chapter(authentication, 2L, 3L);
        assertThat(detail).containsEntry("hasFailedResponseHtml", true);
        assertThat(detail).doesNotContainKey("failedResponseHtml");
    }

    @Test
    void refusesChapterBelongingToAnotherBook() {
        chapter.setCrawlerBook(CrawlerBook.builder().id(9L).build());
        assertThatThrownBy(() -> controller.downloadFailedResponseHtml(authentication, 2L, 3L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void missingSnapshotExplainsThatChapterMustBeRetried() {
        assertThatThrownBy(() -> controller.downloadFailedResponseHtml(authentication, 2L, 3L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("重新采集");
    }
}
