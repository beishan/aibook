package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户界面偏好。
 *
 * 字段允许为空，以便首次升级时由前端把现有本地配置迁移到服务器。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDTO {

    private String theme;
    private String libraryViewMode;
    private Integer scanThreadCount;
}
