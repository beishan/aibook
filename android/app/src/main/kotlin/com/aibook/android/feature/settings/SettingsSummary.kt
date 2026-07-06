package com.aibook.android.feature.settings

object SettingsSummary {
    fun connectionSubtitle(
        serverUrl: String,
        isLoggedIn: Boolean,
        username: String?
    ): String {
        if (serverUrl.isBlank()) return "未配置服务器"
        if (!isLoggedIn) return "已配置服务器，未登录"
        return "已登录：${username?.takeIf { it.isNotBlank() } ?: "当前用户"}"
    }

    fun privacySubtitle(
        personalizedRecommendations: Boolean,
        usageStatistics: Boolean
    ): String {
        val disabledCount = listOf(personalizedRecommendations, usageStatistics).count { !it }
        return when (disabledCount) {
            0 -> "推荐与统计均已开启"
            2 -> "推荐与统计均已关闭"
            else -> "已关闭 $disabledCount 项数据功能"
        }
    }
}
