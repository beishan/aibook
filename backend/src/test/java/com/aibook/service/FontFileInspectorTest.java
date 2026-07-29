package com.aibook.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FontFileInspectorTest {

    @TempDir
    Path tempDir;

    private final FontFileInspector inspector = new FontFileInspector();

    @Test
    void readsFamilyAndStyleFromTrueTypeNameTable() throws Exception {
        Path font = tempDir.resolve("demo.ttf");
        Files.write(font, trueTypeFont("示例字体", "Bold Italic"));

        FontFileInspector.FontMetadata metadata = inspector.inspect(font);

        assertEquals("ttf", metadata.format());
        assertEquals("示例字体", metadata.fontFamily());
        assertEquals(700, metadata.fontWeight());
        assertEquals("italic", metadata.fontStyle());
    }

    @Test
    void rejectsExtensionThatDoesNotMatchHeader() throws Exception {
        Path font = tempDir.resolve("demo.otf");
        Files.write(font, trueTypeFont("Demo", "Regular"));

        assertThrows(IOException.class, () -> inspector.inspect(font));
    }

    private byte[] trueTypeFont(String family, String subfamily) {
        byte[] familyBytes = family.getBytes(StandardCharsets.UTF_16BE);
        byte[] subfamilyBytes = subfamily.getBytes(StandardCharsets.UTF_16BE);
        int nameOffset = 28;
        int stringsOffset = 30;
        ByteBuffer buffer = ByteBuffer.allocate(
                        nameOffset + stringsOffset
                                + familyBytes.length + subfamilyBytes.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x00010000);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.put("name".getBytes(StandardCharsets.ISO_8859_1));
        buffer.putInt(0);
        buffer.putInt(nameOffset);
        buffer.putInt(stringsOffset + familyBytes.length + subfamilyBytes.length);
        buffer.position(nameOffset);
        buffer.putShort((short) 0);
        buffer.putShort((short) 2);
        buffer.putShort((short) stringsOffset);
        putNameRecord(buffer, 1, familyBytes.length, 0);
        putNameRecord(buffer, 2, subfamilyBytes.length, familyBytes.length);
        buffer.put(familyBytes);
        buffer.put(subfamilyBytes);
        return buffer.array();
    }

    private void putNameRecord(
            ByteBuffer buffer, int nameId, int length, int offset) {
        buffer.putShort((short) 3);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0x0409);
        buffer.putShort((short) nameId);
        buffer.putShort((short) length);
        buffer.putShort((short) offset);
    }
}
