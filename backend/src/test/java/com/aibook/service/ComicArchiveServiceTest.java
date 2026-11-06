package com.aibook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ComicArchiveServiceTest {

    @TempDir
    Path directory;

    private final ComicArchiveService service = new ComicArchiveService();

    @Test
    void sortsNestedPathsAndSkipsNonPages() throws IOException {
        Path file = directory.resolve("book.cbz");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            add(zip, "第10话/1.png", png(4));
            add(zip, "第2话/10.png", png(3));
            add(zip, "第2话/2.png", png(2));
            add(zip, ".ignored/0.png", png(8));
            add(zip, "__MACOSX/0.png", png(9));
            add(zip, "ComicInfo.xml", new byte[] {0});
        }

        var archive = service.archive(service.source(file.toString(), null));
        assertEquals(3, archive.pageCount());
        assertEquals(2, service.page(archive, 1).width());
        assertEquals(3, service.page(archive, 2).width());
        assertEquals(4, service.page(archive, 3).width());
    }

    @Test
    void readsRepeatedNamesByTheirOwnCentralDirectoryEntry() throws IOException {
        Path file = directory.resolve("duplicates.cbz");
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            add(zip, "0a.png", png(2));
            add(zip, "0b.png", png(3));
        }
        // ZipOutputStream rejects duplicate names, but the ZIP reader must handle them.
        byte[] duplicate = new String(output.toByteArray(), StandardCharsets.ISO_8859_1)
                .replace("0b.png", "0a.png").getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file, duplicate);

        var archive = service.archive(service.source(file.toString(), null));
        assertEquals(2, service.page(archive, 1).width());
        assertEquals(3, service.page(archive, 2).width());
    }

    @Test
    void leavesBadPageInTheIndexAndFindsTheFirstUsableCover() throws IOException {
        Path file = directory.resolve("bad-page.cbz");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            add(zip, "1.png", new byte[] {0});
            add(zip, "2.png", png(3));
        }
        var source = service.source(file.toString(), null);
        var archive = service.archive(source);
        assertEquals(2, archive.pageCount());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ResponseStatusException.class, () -> service.page(archive, 1)).getStatusCode());
        assertEquals(3, service.cover(source).orElseThrow().width());
        Files.write(file, new byte[] {0});
        assertNotEquals(source.revision(), service.source(file.toString(), null).revision());
    }

    private static byte[] png(int width) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
