package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.WebsiteSettingsDTO;
import com.aibook.dto.WebsiteSettingsUpdateRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class WebsiteSettingsServiceTest {

    @TempDir
    Path tempDir;

    private final Map<String, SystemConfig> configs = new HashMap<>();
    private OperationLogService operationLogService;
    private WebsiteSettingsService service;

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
        service = new WebsiteSettingsService(repository, operationLogService);
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
    }

    @Test
    void returnsDefaultsAndPersistsThemeSpecificStyles() {
        WebsiteSettingsDTO defaults = service.getSettings();
        assertThat(defaults.siteName()).isEqualTo("汗牛充栋");
        assertThat(defaults.registrationEnabled()).isTrue();
        assertThat(defaults.loginStyles().values()).containsOnly("glass");

        WebsiteSettingsDTO updated = service.update(user(), new WebsiteSettingsUpdateRequest(
                "家庭书房",
                "家庭书房 - 登录",
                "在家里阅读每一本好书",
                false,
                Map.of(
                        "modern", "split",
                        "warm", "minimal",
                        "natural", "glass",
                        "macos26", "split")));

        assertThat(updated.siteName()).isEqualTo("家庭书房");
        assertThat(updated.registrationEnabled()).isFalse();
        assertThat(updated.loginStyles().get("warm")).isEqualTo("minimal");
        verify(operationLogService).record(
                any(User.class),
                org.mockito.ArgumentMatchers.eq(OperationLog.Action.UPDATE_SITE_SETTINGS),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("更新网站基本信息"),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void storesAndRestoresValidatedLoginIcon() throws Exception {
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };
        WebsiteSettingsDTO uploaded = service.uploadLoginIcon(user(), new MockMultipartFile(
                "file", "login.png", "image/png", png));

        assertThat(uploaded.hasLoginIcon()).isTrue();
        assertThat(uploaded.loginIconUrl()).isEqualTo("/api/site/login-icon");
        WebsiteSettingsService.LoginIconContent content = service.getLoginIconContent();
        assertThat(Files.readAllBytes(content.path())).isEqualTo(png);

        Path stored = content.path();
        WebsiteSettingsDTO restored = service.restoreDefaultLoginIcon(user());
        assertThat(restored.hasLoginIcon()).isFalse();
        assertThat(stored).doesNotExist();
    }

    @Test
    void rejectsUnknownLoginStyle() {
        assertThatThrownBy(() -> service.update(user(), new WebsiteSettingsUpdateRequest(
                "书房",
                "书房",
                "",
                true,
                Map.of(
                        "modern", "unknown",
                        "warm", "glass",
                        "natural", "glass",
                        "macos26", "glass"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("登录页样式无效");
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
