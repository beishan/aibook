package com.aibook.android.core.network.opds

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>
)

data class OpdsEntry(
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val acquisitionLink: OpdsLink? = null,
    val alternateLink: OpdsLink? = null,
    val coverLink: OpdsLink? = null
)

data class OpdsLink(
    val href: String,
    val type: String? = null,
    val rel: String? = null
)

data class OpdsConnection(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val enabled: Boolean = true,
    val lastSyncedAt: Long? = null,
    val bookCount: Int = 0,
    val syncState: OpdsSyncState = OpdsSyncState.IDLE,
    val lastErrorMessage: String? = null
)

enum class OpdsSyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED
}
