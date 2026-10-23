package com.lichong.calibrereader.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.model.ServerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore("calibre_servers")

class ServerStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 内存快照，供 OkHttp 拦截器同步读取当前激活服务器 */
    val current = MutableStateFlow<ServerConfig?>(null)

    private val prefsFlow: Flow<Preferences> = context.serverDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val servers: Flow<List<ServerConfig>> = prefsFlow.map { prefs ->
        decodeServers(prefs[KEY_SERVERS])
    }

    val activeServer: Flow<ServerConfig?> = combine(servers, prefsFlow.map { it[KEY_ACTIVE_ID] }) { list, activeId ->
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    suspend fun upsert(config: ServerConfig) {
        val normalized =
            if (config.createdAt == 0L) config.copy(createdAt = System.currentTimeMillis()) else config
        context.serverDataStore.edit { prefs ->
            val list = decodeServers(prefs[KEY_SERVERS]).toMutableList()
            val idx = list.indexOfFirst { it.id == normalized.id }
            if (idx >= 0) list[idx] = normalized else list.add(normalized)
            prefs[KEY_SERVERS] = encodeServers(list)
            if (prefs[KEY_ACTIVE_ID].isNullOrBlank()) prefs[KEY_ACTIVE_ID] = normalized.id
        }
        primeCache()
    }

    suspend fun delete(id: String) {
        context.serverDataStore.edit { prefs ->
            val list = decodeServers(prefs[KEY_SERVERS]).filterNot { it.id == id }
            prefs[KEY_SERVERS] = encodeServers(list)
            if (prefs[KEY_ACTIVE_ID] == id) prefs[KEY_ACTIVE_ID] = list.firstOrNull()?.id ?: ""
        }
        primeCache()
    }

    suspend fun setActive(id: String) {
        context.serverDataStore.edit { prefs -> prefs[KEY_ACTIVE_ID] = id }
        primeCache()
    }

    /** 把激活服务器灌进内存快照，失败静默（拦截器退化为无认证） */
    suspend fun primeCache() {
        try {
            val list = servers.first()
            val activeId = prefsFlow.map { it[KEY_ACTIVE_ID] }.first()
            current.value = list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "primeCache failed", e)
        }
    }

    fun updateCache(config: ServerConfig?) {
        current.value = config
    }

    private fun decodeServers(raw: String?): List<ServerConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(ServerDto.serializer()), raw).map { it.toConfig() }
        } catch (e: Exception) {
            Log.w(TAG, "decode servers failed", e)
            emptyList()
        }
    }

    private fun encodeServers(list: List<ServerConfig>): String {
        val dtos: List<ServerDto> = list.map { ServerDto.from(it) }
        return json.encodeToString(ListSerializer(ServerDto.serializer()), dtos)
    }

    @Serializable
    private data class ServerDto(
        val id: String,
        val name: String = "",
        val baseUrl: String = "",
        val username: String = "",
        val password: String = "",
        val opdsRootUrl: String = "",
        val kind: String = "UNKNOWN",
        val trustAllCerts: Boolean = false,
        val koboToken: String = "",
        val createdAt: Long = 0L
    ) {
        fun toConfig() = ServerConfig(
            id = id,
            name = name,
            baseUrl = baseUrl,
            username = username,
            password = CryptoBox.decrypt(password),
            opdsRootUrl = opdsRootUrl,
            kind = runCatching { ServerKind.valueOf(kind) }.getOrDefault(ServerKind.UNKNOWN),
            trustAllCerts = trustAllCerts,
            koboToken = koboToken,
            createdAt = createdAt
        )

        companion object {
            fun from(c: ServerConfig) = ServerDto(
                id = c.id,
                name = c.name,
                baseUrl = c.baseUrl,
                username = c.username,
                password = CryptoBox.encrypt(c.password),
                opdsRootUrl = c.opdsRootUrl,
                kind = c.kind.name,
                trustAllCerts = c.trustAllCerts,
                koboToken = c.koboToken,
                createdAt = c.createdAt
            )
        }
    }

    companion object {
        private const val TAG = "ServerStore"
        private val KEY_SERVERS = stringPreferencesKey("servers_json")
        private val KEY_ACTIVE_ID = stringPreferencesKey("active_server_id")

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
    }
}
