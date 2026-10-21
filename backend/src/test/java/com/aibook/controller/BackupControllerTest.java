package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibook.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BackupControllerTest {

    @Test
    void deleteExecutionIsMappedAndDelegatesToService() throws Exception {
        BackupService service = mock(BackupService.class);
        MockMvcBuilders.standaloneSetup(new BackupController(service)).build()
                .perform(delete("/api/system/backups/executions/12"))
                .andExpect(status().isNoContent());
        verify(service).deleteExecution(12L);
    }

    @Test
    void backupEndpointsRequireAdministratorRole() {
        assertThat(BackupController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }
}
