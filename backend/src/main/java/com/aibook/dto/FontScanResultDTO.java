package com.aibook.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FontScanResultDTO {
    private int scanned;
    private int newFonts;
    private int updatedFonts;
    private int skippedFonts;
    private int failedFonts;
    private List<String> errors;
}
