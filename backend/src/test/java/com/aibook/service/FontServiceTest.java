package com.aibook.service;

import com.aibook.model.entity.FontAsset;
import com.aibook.model.entity.FontScanDirectory;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.FontScanDirectoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void prefersOriginalFilenameOverEmbeddedFontFamilyForDisplayName() throws Exception {
        FontAssetRepository assets = mock(FontAssetRepository.class);
        FontFileInspector inspector = mock(FontFileInspector.class);
        when(assets.findByFileHash(any())).thenReturn(Optional.empty());
        when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inspector.hasSupportedExtension(any())).thenReturn(true);
        when(inspector.inspect(any(), eq("霞鹜文楷.ttf")))
                .thenReturn(new FontFileInspector.FontMetadata(
                        "ttf", "Embedded Internal Name", 400, "normal"));
        FontService service = new FontService(
                assets,
                mock(FontScanDirectoryRepository.class),
                inspector);
        ReflectionTestUtils.setField(service, "uploadRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFontSize", 1024L * 1024);

        var result = service.upload(List.of(new MockMultipartFile(
                "files", "霞鹜文楷.ttf", "font/ttf", new byte[20])));

        assertEquals("霞鹜文楷", result.getFirst().getDisplayName());
        assertEquals("Embedded Internal Name", result.getFirst().getFontFamily());
    }

    @Test
    void scanPrefersFilenameOverEmbeddedFontFamilyForDisplayName() throws Exception {
        Path scanRoot = Files.createDirectory(tempDir.resolve("fontfolder"));
        Path directoryPath = Files.createDirectory(scanRoot.resolve("custom"));
        Path fontPath = Files.write(directoryPath.resolve("方正书宋.ttf"), new byte[20])
                .toRealPath();
        FontAssetRepository assets = mock(FontAssetRepository.class);
        FontScanDirectoryRepository directories = mock(FontScanDirectoryRepository.class);
        FontFileInspector inspector = mock(FontFileInspector.class);
        FontScanDirectory directory = FontScanDirectory.builder()
                .id(1L)
                .path(directoryPath.toString())
                .enabled(true)
                .build();
        when(directories.findByEnabledTrueOrderByPathAsc()).thenReturn(List.of(directory));
        when(assets.findBySourceTypeAndFilePath(
                FontAsset.SourceType.SCANNED, fontPath.toString()))
                .thenReturn(Optional.empty());
        when(assets.findByFileHash(any())).thenReturn(Optional.empty());
        when(assets.findByScanDirectoryId(1L)).thenReturn(List.of());
        when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inspector.hasSupportedExtension(any())).thenReturn(true);
        when(inspector.inspect(fontPath))
                .thenReturn(new FontFileInspector.FontMetadata(
                        "ttf", "Wrong Internal Name", 400, "normal"));
        FontService service = new FontService(assets, directories, inspector);
        ReflectionTestUtils.setField(service, "fontScanRoot", scanRoot.toString());
        ReflectionTestUtils.setField(service, "maxFontSize", 1024L * 1024);

        service.scan();

        ArgumentCaptor<FontAsset> saved = ArgumentCaptor.forClass(FontAsset.class);
        verify(assets).save(saved.capture());
        assertEquals("方正书宋", saved.getValue().getDisplayName());
        assertEquals("Wrong Internal Name", saved.getValue().getFontFamily());
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
    void browsesOnlyDirectoriesInsideConfiguredFontRoot() throws Exception {
        Path scanRoot = Files.createDirectory(tempDir.resolve("fontfolder"));
        Path chinese = Files.createDirectory(scanRoot.resolve("中文字体"));
        Files.createDirectory(chinese.resolve("宋体"));
        Files.createDirectory(scanRoot.resolve(".hidden"));
        Files.writeString(scanRoot.resolve("readme.txt"), "not a directory");
        FontService service = new FontService(
                mock(FontAssetRepository.class),
                mock(FontScanDirectoryRepository.class),
                new FontFileInspector());
        ReflectionTestUtils.setField(service, "fontScanRoot", scanRoot.toString());

        var roots = service.browseDirectories(null);
        var children = service.browseDirectories(scanRoot.toString());

        assertEquals(1, roots.size());
        assertEquals(scanRoot.toRealPath().toString(), roots.getFirst().getPath());
        assertEquals("中文字体", children.getFirst().getName());
        assertFalse(children.getFirst().getLeaf());
        assertEquals(1, children.size());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.browseDirectories(tempDir.toString()));
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
