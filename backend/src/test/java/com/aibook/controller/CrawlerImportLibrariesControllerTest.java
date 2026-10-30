package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerExportService;
import com.aibook.service.crawler.CrawlerImportLibrariesService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrawlerImportLibrariesControllerTest {
    private final UserService users = mock(UserService.class);
    private final CrawlerImportLibrariesService service = mock(CrawlerImportLibrariesService.class);
    private final CrawlerExportService exports = mock(CrawlerExportService.class);
    private final CrawlerImportLibrariesController controller =
            new CrawlerImportLibrariesController(service, exports, users);
    private final Authentication auth = mock(Authentication.class);
    private final User user = User.builder().id(1L).build();

    private void authenticated() {
        when(auth.getName()).thenReturn("reader");
        when(users.findByUsername("reader")).thenReturn(user);
    }

    @Test
    void savesSelectionBeforeCheckingWhetherCompletedBookCanBeImportedImmediately() {
        authenticated();
        when(service.save(user, 3L, List.of(7L, 8L))).thenReturn(List.of(7L, 8L));
        assertThat(controller.save(auth, 3L,
                new CrawlerImportLibrariesController.Selection(List.of(7L, 8L))))
                .containsExactly(7L, 8L);
        var order = inOrder(service, exports);
        order.verify(service).save(user, 3L, List.of(7L, 8L));
        order.verify(exports).autoImportCompleted(user, 3L);
    }

    @Test
    void clearingSelectionDoesNotTriggerImport() {
        authenticated();
        when(service.save(user, 3L, List.of())).thenReturn(List.of());
        assertThat(controller.save(auth, 3L,
                new CrawlerImportLibrariesController.Selection(List.of()))).isEmpty();
        verifyNoInteractions(exports);
    }

    @Test
    void doesNotReportSuccessWhenImmediateImportFails() {
        authenticated();
        when(service.save(user, 3L, List.of(7L))).thenReturn(List.of(7L));
        when(exports.autoImportCompleted(user, 3L)).thenThrow(new IllegalStateException("入库失败"));
        assertThatThrownBy(() -> controller.save(auth, 3L,
                new CrawlerImportLibrariesController.Selection(List.of(7L))))
                .hasMessage("入库失败");
    }
}
