package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aibook.dto.DockIconStatusDTO;
import com.aibook.model.entity.User;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class DockIconServiceTest {

    @TempDir
    Path tempDir;

    private DockIconService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new DockIconService();
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
        user = User.builder().id(7L).username("reader").build();
    }

    @Test
    void storesReadsAndDeletesCustomIcon() throws Exception {
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };

        DockIconStatusDTO status = service.upload(user, "library", new MockMultipartFile(
                "file", "library.png", "image/png", png));

        assertThat(status.icons()).containsExactly("library");
        DockIconService.DockIconContent content = service.getIcon(user, "library");
        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(content.path())).isEqualTo(png);

        assertThat(service.delete(user, "library").icons()).isEmpty();
        assertThat(content.path()).doesNotExist();
    }

    @Test
    void replacesPreviousIconFormat() {
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        service.upload(user, "home", new MockMultipartFile(
                "file", "home.png", "image/png", png));

        service.upload(user, "home", new MockMultipartFile(
                "file", "home.jpg", "image/jpeg", jpeg));

        assertThat(service.getIcon(user, "home").contentType()).isEqualTo("image/jpeg");
        assertThat(tempDir.resolve("dock-icons/7/home.png")).doesNotExist();
    }

    @Test
    void rejectsInvalidNameAndFakeImage() {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());

        assertBadRequest(() -> service.upload(user, "other", fake));
        assertBadRequest(() -> service.upload(user, "settings", fake));
    }

    private void assertBadRequest(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
