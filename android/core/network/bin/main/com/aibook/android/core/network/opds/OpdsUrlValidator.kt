package com.aibook.android.core.network.opds

import java.net.IDN
import java.net.URI

object OpdsUrlValidator {
    fun normalize(value: String): Result<String> = runCatching {
        val trimmed = value.trim()
        require(trimmed.length <= 2048) { "URL 过长" }
        val uri = URI(trimmed)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "仅支持 http 或 https 地址" }
        require(!uri.host.isNullOrBlank()) { "URL 必须包含有效主机名" }
        require(uri.rawUserInfo == null) { "请在认证字段中填写用户名和密码" }
        require(uri.rawFragment == null) { "URL 不能包含片段标识" }
        require(uri.port in -1..65535) { "端口号无效" }
        val asciiHost = IDN.toASCII(uri.host.lowercase())
        URI(
            uri.scheme.lowercase(),
            null,
            asciiHost,
            uri.port,
            uri.rawPath?.ifBlank { "/" } ?: "/",
            uri.rawQuery,
            null
        ).toASCIIString()
    }
}
