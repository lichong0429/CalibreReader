package com.lichong.calibrereader.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ServerKind {
    CALIBRE_WEB,
    CALIBRE_SERVER,
    UNKNOWN;

    val label: String
        get() = when (this) {
            CALIBRE_WEB -> "Calibre-Web"
            CALIBRE_SERVER -> "calibre-server"
            UNKNOWN -> "未知类型"
        }
}

@Serializable
data class ServerConfig(
    val id: String,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val opdsRootUrl: String = "",
    val kind: ServerKind = ServerKind.UNKNOWN,
    val trustAllCerts: Boolean = false,
    /** Calibre-Web 用户设置里的 Kobo 授权令牌，用于阅读进度双向同步 */
    val koboToken: String = "",
    val createdAt: Long = 0L
) {
    val displayName: String
        get() = name.ifBlank { baseUrl.ifBlank { "未命名" } }

    val hasAuth: Boolean
        get() = username.isNotBlank()

    val canSyncProgress: Boolean
        get() = kind == ServerKind.CALIBRE_WEB
}
