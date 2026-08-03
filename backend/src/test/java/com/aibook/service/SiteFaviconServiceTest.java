package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.SiteFaviconStatusDTO;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.SystemConfig;
import com.aibook.model.entity.User;
import com.aibook.repository.SystemConfigRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class SiteFaviconServiceTest {

    @TempDir
    Path tempDir;

    private final Map<String, SystemConfig> configs = new HashMap<>();
    private OperationLogService operationLogService;
    private SiteFaviconService service;

    @BeforeEach
    void setUp() {
        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        operationLogService = mock(OperationLogService.class);
        when(repository.findById(any(String.class)))
                .thenAnswer(invocation -> Optional.ofNullable(configs.get(invocation.getArgument(0))));
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> {
            SystemConfig config = invocation.getArgument(0);
            configs.put(config.getConfigKey(), config);
            return config;
        });
        service = new SiteFaviconService(repository, operationLogService);
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
    }

    @Test
    void storesAndServesValidatedFavicon() throws Exception {
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };

        SiteFaviconStatusDTO status = service.upload(user(), new MockMultipartFile(
                "file", "favicon.png", "image/png", png));

        assertThat(status.hasCustom()).isTrue();
        assertThat(status.url()).isEqualTo("/api/site/favicon");
        SiteFaviconService.FaviconContent content = service.getContent();
        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(content.path())).isEqualTo(png);
        verify(operationLogService).record(
                any(User.class),
                org.mockito.ArgumentMatchers.eq(OperationLog.Action.UPDATE_SITE_FAVICON),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("更新网站标签页图标"),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void restoresDefaultAndDeletesCustomFile() {
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };
        service.upload(user(), new MockMultipartFile(
                "file", "favicon.png", "image/png", png));
        Path stored = service.getContent().path();

        SiteFaviconStatusDTO status = service.restoreDefault(user());

        assertThat(status.hasCustom()).isFalse();
        assertThat(stored).doesNotExist();
    }

    @Test
    void rejectsFileWhoseContentIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload(user(), file))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("admin")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
    }
}
