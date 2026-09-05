package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.ReaderBackgroundDTO;
import com.aibook.model.entity.ReaderBackground;
import com.aibook.model.entity.User;
import com.aibook.repository.ReaderBackgroundRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ReaderBackgroundServiceTest {

    @TempDir
    Path tempDirectory;

    private ReaderBackgroundRepository backgroundRepository;
    private ReaderBackgroundService service;
    private User user;

    @BeforeEach
    void setUp() {
        backgroundRepository = Mockito.mock(ReaderBackgroundRepository.class);
        service = new ReaderBackgroundService(backgroundRepository);
        ReflectionTestUtils.setField(service, "uploadDir", tempDirectory.toString());
        ReflectionTestUtils.setField(service, "coverDir", "covers");
        user = User.builder().id(7L).username("reader").build();
    }

    @Test
    void uploadsValidatedPngForCurrentUser() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile file = new MockMultipartFile(
                "files", "paper.png", "image/png", png);
        when(backgroundRepository.save(Mockito.any(ReaderBackground.class)))
                .thenAnswer(invocation -> {
                    ReaderBackground background = invocation.getArgument(0);
                    background.setId(12L);
                    return background;
                });

        List<ReaderBackgroundDTO> uploaded = service.upload(user, List.of(file));

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.getFirst().name()).isEqualTo("paper.png");
        assertThat(uploaded.getFirst().imageUrl()).startsWith("covers/reader-background-7-");
        assertThat(tempDirectory.resolve(uploaded.getFirst().imageUrl())).exists().hasBinaryContent(png);
    }

    @Test
    void rejectsUnsupportedContentEvenWhenExtensionLooksValid() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload(user, List.of(file)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("仅支持 JPG、PNG、WebP 或 GIF");
    }

    @Test
    void deletesOnlyBackgroundOwnedByCurrentUser() throws Exception {
        Files.createDirectories(tempDirectory.resolve("covers"));
        Path stored = tempDirectory.resolve("covers/reader-background.png");
        Files.write(stored, new byte[] {1, 2, 3});
        ReaderBackground background = ReaderBackground.builder()
                .id(3L)
                .user(user)
                .storedFilename("reader-background.png")
                .originalFilename("reader-background.png")
                .contentType("image/png")
                .fileSize(3L)
                .build();
        when(backgroundRepository.findByIdAndUser(3L, user)).thenReturn(java.util.Optional.of(background));

        service.delete(user, 3L);

        verify(backgroundRepository).delete(background);
        assertThat(stored).doesNotExist();
    }
}
