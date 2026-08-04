package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SystemResourcesControllerTest {

    @Test
    void resourcesRequireAdministratorRole() throws Exception {
        assertThat(SystemResourcesController.class.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(SystemResourcesController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");

        Method resources = SystemResourcesController.class.getMethod("resources");
        assertThat(resources.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class))
                .isNotNull();
    }
}
