package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SiteFaviconControllerTest {

    @Test
    void uploadAndRestoreRequireAdministratorRole() throws Exception {
        Method upload = SiteFaviconController.class.getMethod(
                "upload",
                org.springframework.security.core.Authentication.class,
                org.springframework.web.multipart.MultipartFile.class);
        Method restore = SiteFaviconController.class.getMethod(
                "restoreDefault",
                org.springframework.security.core.Authentication.class);

        assertThat(upload.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
        assertThat(restore.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }
}
