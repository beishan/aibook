package com.aibook.android.feature.reader

import com.aibook.android.core.reader.BookContentError

object BookContentErrorText {
    fun forError(error: BookContentError): String = when (error) {
        BookContentError.FileMissing -> "文件不存在，请重新导入"
        BookContentError.PermissionLost -> "文件权限已失效，请重新导入"
        BookContentError.DrmProtected -> "此书包含 DRM，当前仅支持无 DRM 的 MOBI/AZW3 文件"
        BookContentError.PasswordProtected -> "文件受密码保护，当前仅支持未加密文件"
        BookContentError.UnsupportedVariant -> "暂不支持此文件变体，可转换为 EPUB 或 PDF 后重试"
        BookContentError.CorruptedFile -> "文件已损坏或内容无法解析，请重新导入后重试"
        BookContentError.InsufficientStorage -> "存储空间不足，请清理空间后重试"
        is BookContentError.ParseFailed -> "文件解析失败，请重新导入后重试"
    }
}
