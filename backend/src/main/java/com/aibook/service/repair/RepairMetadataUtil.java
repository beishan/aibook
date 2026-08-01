package com.aibook.service.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds structured detector metadata used by the repair preview UI. */
final class RepairMetadataUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RepairMetadataUtil() {
    }

    static String blankLineContext(
            int blankLineCount, String contextBefore, String contextAfter) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("blankLineCount", blankLineCount);
        metadata.put("contextBefore", contextBefore == null ? "" : contextBefore);
        metadata.put("contextAfter", contextAfter == null ? "" : contextAfter);
        return toJson(metadata);
    }

    static String samplePreview(String original, String suggested, String note) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previewOriginal", original);
        metadata.put("previewSuggested", suggested);
        if (note != null && !note.isBlank()) {
            metadata.put("previewNote", note);
        }
        return toJson(metadata);
    }

    static String encodingCandidates(String garbledPattern, List<String> candidates) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("garbledPattern", garbledPattern);
        metadata.put("candidates", candidates);
        metadata.put("previewOriginal", garbledPattern);
        if (candidates != null && !candidates.isEmpty()) {
            metadata.put("previewSuggested", candidates.get(0));
        }
        metadata.put("previewNote", "接受后将替换全文中相同的乱码特征");
        return toJson(metadata);
    }

    private static String toJson(Map<String, Object> metadata) {
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法生成修复预览元数据", exception);
        }
    }
}
