package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FontDirectoryNodeDTO {
    private String name;
    private String path;
    private Boolean leaf;
}
