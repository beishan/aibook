package com.aibook.service;

import com.aibook.model.entity.FontAsset;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.FontScanDirectoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FontServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadsValidatedFontIntoPersistentFontDirectory() throws Exception {
        FontAssetRepository assets = mock(FontAssetRepository.class);
        when(assets.findByFileHash(any())).thenReturn(Optional.empty());
        when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FontService service = new FontService(
                assets,
                mock(FontScanDirectoryRepository.class),
                new FontFileInspector());
        ReflectionTestUtils.setField(service, "uploadRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFontSize", 1024L * 1024);

        byte[] minimalWoff2 = ByteBuffer.allocate(48)
                .put((byte) 'w')
                .put((byte) 'O')
                .put((byte) 'F')
                .put((byte) '2')
                .putInt(0x00010000)
                .putInt(48)
                .putShort((short) 1)
                .putShort((short) 0)
                .putInt(12)
                .array();
        var result = service.upload(List.of(new MockMultipartFile(
                "files", "Demo.woff2", "font/woff2", minimalWoff2)));

        assertEquals(1, result.size());
        assertEquals("woff2", result.getFirst().getFormat());
        assertEquals("Demo", result.getFirst().getFontFamily());
        assertTrue(Files.isDirectory(tempDir.resolve("fonts")));
    }

    @Test
    void rejectsUnsupportedUploadExtension() {
        FontService service = new FontService(
                mock(FontAssetRepository.class),
                mock(FontScanDirectoryRepository.class),
                new FontFileInspector());
        ReflectionTestUtils.setField(service, "uploadRoot", tempDir.toString());

        assertThrows(
                ResponseStatusException.class,
                () -> service.upload(List.of(new MockMultipartFile(
                        "files", "font.exe", "application/octet-stream",
                        new byte[20]))));
    }

    @Test
    void rejectsRelativeScanDirectory() {
        FontService service = new FontService(
                mock(FontAssetRepository.class),
                mock(FontScanDirectoryRepository.class),
                new FontFileInspector());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addDirectory("relative/fonts"));
    }

    @Test
    void rejectsDirectoryOutsideConfiguredScanRoot() throws Exception {
        Path scanRoot = Files.createDirectory(tempDir.resolve("fontfolder"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        FontService service = new FontService(
                mock(FontAssetRepository.class),
                mock(FontScanDirectoryRepository.class),
                new FontFileInspector());
        ReflectionTestUtils.setField(service, "fontScanRoot", scanRoot.toString());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addDirectory(outside.toString()));
        assertTrue(exception.getMessage().contains("必须位于扫描根目录"));
    }
}
