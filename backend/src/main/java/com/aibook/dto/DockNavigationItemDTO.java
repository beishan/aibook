package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DockNavigationItemDTO {
    private String key;
    private String title;
    private String icon;
    private Boolean enabled;
    private Integer order;
}
