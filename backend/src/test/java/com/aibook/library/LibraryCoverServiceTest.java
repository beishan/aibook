package com.aibook.library;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class LibraryCoverServiceTest {
    @org.junit.jupiter.api.BeforeAll
    static void useHeadlessImageProcessing() {
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir Path root;
    private final LibraryService libraries = mock(LibraryService.class);
    private final LibraryCoverService covers = new LibraryCoverService(libraries);
    private final User user = User.builder().id(1L).build();
    private Map<String, Object> settings;
    private Map<String, Object> library;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(covers, "uploadPath", root.toString());
        settings = new HashMap<>(Map.of("cover", "arches", "layout", "grid"));
        library = new HashMap<>(Map.of("name", "馆", "description", "简介", "config", settings, "revision", 3L));
        when(libraries.owned(user, 7L, false)).thenReturn(library);
        when(libraries.save(eq(user), eq(7L), eq(3L), eq("馆"), eq("简介"), anyMap())).thenAnswer(call -> {
            library.put("config", new HashMap<>(call.getArgument(5)));
            return library;
        });
    }

    @AfterEach
    void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        return new MockMultipartFile("file", "cover.png", "image/png", bytes.toByteArray());
    }

    private Path seedOldImage() throws Exception {
        String key = UUID.randomUUID().toString();
        settings.put("coverImage", key);
        Path path = root.resolve("library-covers/1/7/" + key + ".jpg");
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[] {1});
        return path;
    }

    @Test
    void validatesImageReencodesAndPreservesOtherConfiguration() throws Exception {
        covers.upload(user, 7L, 3L, image());
        Path image = covers.image(user, 7L);
        assertThat(image).exists();
        assertThat(ImageIO.read(image.toFile()).getWidth()).isEqualTo(40);
        assertThat(((Map<?, ?>) library.get("config")).get("cover")).isEqualTo("arches");
        verify(libraries).save(eq(user), eq(7L), eq(3L), eq("馆"), eq("简介"), anyMap());
    }

    @Test
    void rejectsSpoofedOversizedAndUnsupportedImages() throws Exception {
        assertThatThrownBy(() -> covers.upload(user, 7L, 3L,
                new MockMultipartFile("file", "fake.png", "image/png", "not-image".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> covers.upload(user, 7L, 3L,
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(IllegalArgumentException.class);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", bytes);
        assertThatThrownBy(() -> covers.upload(user, 7L, 3L,
                new MockMultipartFile("file", "fake.png", "image/png", bytes.toByteArray())))
                .isInstanceOf(IllegalArgumentException.class);
        var tooWide = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6001, 1, BufferedImage.TYPE_INT_RGB), "png", tooWide);
        assertThatThrownBy(() -> covers.upload(user, 7L, 3L,
                new MockMultipartFile("file", "wide.png", "image/png", tooWide.toByteArray())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(libraries, never()).save(any(), anyLong(), anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void rollbackDeletesNewAssetAndKeepsOldImage() throws Exception {
        Path old = seedOldImage();
        TransactionSynchronizationManager.initSynchronization();
        covers.upload(user, 7L, 3L, image());
        Path created = covers.image(user, 7L);
        assertThat(old).exists();
        TransactionSynchronizationManager.getSynchronizations().forEach(callback ->
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(created).doesNotExist();
        assertThat(old).exists();
    }

    @Test
    void committedReplacementAndRestoreCleanOldAsset() throws Exception {
        Path old = seedOldImage();
        TransactionSynchronizationManager.initSynchronization();
        covers.upload(user, 7L, 3L, image());
        assertThat(old).exists();
        TransactionSynchronizationManager.getSynchronizations().forEach(callback ->
                callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        assertThat(old).doesNotExist();
        Path current = covers.image(user, 7L);
        covers.remove(user, 7L, 3L);
        assertThat(current).doesNotExist();
        assertThat(((Map<?, ?>) library.get("config")).containsKey("coverImage")).isFalse();
    }

    @Test
    void revisionConflictLeavesOldFileAndRemovesNewFile() throws Exception {
        Path old = seedOldImage();
        when(libraries.save(eq(user), eq(7L), eq(2L), anyString(), anyString(), anyMap()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "revision"));
        assertThatThrownBy(() -> covers.upload(user, 7L, 2L, image())).isInstanceOf(ResponseStatusException.class);
        try (var files = Files.list(old.getParent())) { assertThat(files.toList()).containsExactly(old); }
    }

    @Test
    void missingOwnershipAndInvalidKeysCannotReadOtherAccountsFiles() throws Exception {
        seedOldImage();
        var other = User.builder().id(2L).build();
        when(libraries.owned(other, 7L, false)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> covers.image(other, 7L)).isInstanceOf(ResponseStatusException.class);
        settings.put("coverImage", "../../private");
        assertThatThrownBy(() -> covers.image(user, 7L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void identicalImageKeyInAnotherAccountsLibraryCannotReadTheOriginalFile() throws Exception {
        Path original = seedOldImage();
        var other = User.builder().id(2L).build();
        when(libraries.owned(other, 8L, false)).thenReturn(library);
        assertThatThrownBy(() -> covers.image(other, 8L)).isInstanceOf(ResponseStatusException.class);
        assertThat(original).exists();
    }

    @Test
    void deletedLibraryCleanupOnlyRemovesItsOwnDirectory() throws Exception {
        Path old = seedOldImage();
        Path other = root.resolve("library-covers/2/8/other.jpg");
        Files.createDirectories(other.getParent());
        Files.write(other, new byte[] {1});
        covers.cleanupDeletedLibrary(user, 7L);
        assertThat(old).doesNotExist();
        assertThat(other).exists();
    }

    @Test
    void coverIdentifiersCannotContainExternalUrlsOrPathTraversal() {
        var service = new LibraryService(mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class), new ObjectMapper());
        assertThatThrownBy(() -> service.encode(Map.of("coverImage", "https://example.com/image.png")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> service.encode(Map.of("coverImage", UUID.randomUUID().toString())))
                .doesNotThrowAnyException();
    }
}
