package com.aibook.android.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibook.android.core.data.security.PassthroughSecretCipher
import com.aibook.android.core.data.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serverConfigStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

class ServerConfigStore(
    private val context: Context,
    private val secretCipher: SecretCipher = PassthroughSecretCipher
) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val JWT_TOKEN = stringPreferencesKey("jwt_token")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_sync")
        val PERSONALIZED_RECOMMENDATIONS = booleanPreferencesKey("personalized_recommendations")
        val USAGE_STATISTICS = booleanPreferencesKey("usage_statistics")
    }

    val serverUrl: Flow<String> = context.serverConfigStore.data.map { it[Keys.SERVER_URL] ?: "" }
    val jwtToken: Flow<String?> = context.serverConfigStore.data.map { preferences ->
        preferences[Keys.JWT_TOKEN]?.let { stored ->
            runCatching { secretCipher.decrypt(stored) }.getOrNull()
        }
    }
    val username: Flow<String?> = context.serverConfigStore.data.map { it[Keys.USERNAME] }
    val email: Flow<String?> = context.serverConfigStore.data.map { it[Keys.EMAIL] }
    val wifiOnlySync: Flow<Boolean> = context.serverConfigStore.data.map { it[Keys.WIFI_ONLY] ?: true }
    val personalizedRecommendations: Flow<Boolean> =
        context.serverConfigStore.data.map { it[Keys.PERSONALIZED_RECOMMENDATIONS] ?: true }
    val usageStatistics: Flow<Boolean> =
        context.serverConfigStore.data.map { it[Keys.USAGE_STATISTICS] ?: true }

    val isLoggedIn: Flow<Boolean> = jwtToken.map { !it.isNullOrBlank() }

    suspend fun setServerUrl(url: String) {
        context.serverConfigStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun setAuth(token: String, username: String?, email: String?) {
        context.serverConfigStore.edit { prefs ->
            prefs[Keys.JWT_TOKEN] = secretCipher.encrypt(token)
            if (username != null) prefs[Keys.USERNAME] = username
            if (email != null) prefs[Keys.EMAIL] = email
        }
    }

    suspend fun clearAuth() {
        context.serverConfigStore.edit { prefs ->
            prefs.remove(Keys.JWT_TOKEN)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.EMAIL)
        }
    }

    suspend fun setWifiOnlySync(enabled: Boolean) {
        context.serverConfigStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setPersonalizedRecommendations(enabled: Boolean) {
        context.serverConfigStore.edit { it[Keys.PERSONALIZED_RECOMMENDATIONS] = enabled }
    }

    suspend fun setUsageStatistics(enabled: Boolean) {
        context.serverConfigStore.edit { it[Keys.USAGE_STATISTICS] = enabled }
    }

    suspend fun tokenSync(): String? {
        return jwtToken.first()
    }

    suspend fun migratePlaintextToken() {
        val stored = context.serverConfigStore.data.first()[Keys.JWT_TOKEN] ?: return
        if (!secretCipher.isEncrypted(stored)) {
            context.serverConfigStore.edit { it[Keys.JWT_TOKEN] = secretCipher.encrypt(stored) }
        }
    }
}
