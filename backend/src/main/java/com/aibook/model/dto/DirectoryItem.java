package com.aibook.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目录浏览项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryItem {

    /**
     * 目录/文件名
     */
    private String name;

    /**
     * 完整路径
     */
    private String path;

    /**
     * 是否为目录
     */
    @JsonProperty("isDirectory")
    private boolean isDirectory;

    /**
     * 文件大小（字节）
     */
    private long size;

    /**
     * 类型描述（目录/文件）
     */
    private String type;

    /**
     * 是否可访问（用于控制前端是否显示选择按钮）
     */
    private boolean accessible;
}
