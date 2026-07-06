package com.aibook.android.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSummaryTest {

    @Test
    fun connectionSubtitleShowsMissingServer() {
        assertEquals(
            "未配置服务器",
            SettingsSummary.connectionSubtitle(serverUrl = "", isLoggedIn = false, username = null)
        )
    }

    @Test
    fun connectionSubtitleShowsConfiguredServerBeforeLogin() {
        assertEquals(
            "已配置服务器，未登录",
            SettingsSummary.connectionSubtitle(
                serverUrl = "http://192.168.1.10:8080",
                isLoggedIn = false,
                username = null
            )
        )
    }

    @Test
    fun connectionSubtitleShowsLoggedInUser() {
        assertEquals(
            "已登录：beibei",
            SettingsSummary.connectionSubtitle(
                serverUrl = "http://192.168.1.10:8080",
                isLoggedIn = true,
                username = "beibei"
            )
        )
    }

    @Test
    fun privacySubtitleShowsAllEnabled() {
        assertEquals(
            "推荐与统计均已开启",
            SettingsSummary.privacySubtitle(personalizedRecommendations = true, usageStatistics = true)
        )
    }

    @Test
    fun privacySubtitleShowsPartiallyEnabled() {
        assertEquals(
            "已关闭 1 项数据功能",
            SettingsSummary.privacySubtitle(personalizedRecommendations = false, usageStatistics = true)
        )
    }

    @Test
    fun privacySubtitleShowsAllDisabled() {
        assertEquals(
            "推荐与统计均已关闭",
            SettingsSummary.privacySubtitle(personalizedRecommendations = false, usageStatistics = false)
        )
    }
}
