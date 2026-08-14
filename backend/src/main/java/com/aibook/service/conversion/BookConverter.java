package com.aibook.service.conversion;

import com.aibook.model.entity.BookConversionTask;
import java.nio.file.Path;

/** 可按源/目标格式扩展的转换器接口。 */
public interface BookConverter {
    boolean supports(String sourceFormat, String targetFormat);
    void convert(BookConversionTask task, Path output) throws Exception;
}
