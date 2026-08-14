package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class WebsiteSettingsControllerTest {

    @Test
    void updatesAndLoginIconChangesRequireAdministratorRole() throws Exception {
        Method update = WebsiteSettingsController.class.getMethod(
                "update",
                org.springframework.security.core.Authentication.class,
                com.aibook.dto.WebsiteSettingsUpdateRequest.class);
        Method upload = WebsiteSettingsController.class.getMethod(
                "uploadLoginIcon",
                org.springframework.security.core.Authentication.class,
                org.springframework.web.multipart.MultipartFile.class);
        Method restore = WebsiteSettingsController.class.getMethod(
                "restoreDefaultLoginIcon",
                org.springframework.security.core.Authentication.class);

        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(upload.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(restore.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
