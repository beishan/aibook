package com.aibook.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 切换编码预览请求
 */
@Data
public class SwitchEncodingRequest {

    @NotNull
    private Long bookId;

    /** 书籍版本 ID，可选 */
    private Long versionId;

    /** 指定编码名称：UTF-8 / GBK / GB18030 / Big5 / UTF-16LE / UTF-16BE / AUTO */
    @NotNull
    private String encoding;
}
