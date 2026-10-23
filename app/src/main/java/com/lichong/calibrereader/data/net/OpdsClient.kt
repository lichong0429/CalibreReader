package com.lichong.calibrereader.data.net

import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.opds.OpdsFeed
import com.lichong.calibrereader.data.opds.OpdsParseException
import com.lichong.calibrereader.data.opds.OpdsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class OpdsHttpException(
    val code: Int,
    val statusMessage: String,
    val url: String
) : IOException("HTTP $code $statusMessage")

data class OpdsResponse(
    val feed: OpdsFeed,
    val finalUrl: String,
    val serverHeader: String?,
    val contentType: String?
)

/**
 * OPDS 请求客户端。三种 HTTP 行为差异：
 * - Basic 认证（Calibre-Web / 开启 auth 的 calibre-server）
 * - 明文 http（自建服务常态）
 * - 自签证书（trustAll 开关）
 */
class OpdsClient(
    private val cacheDir: File,
    private val activeConfig: () -> ServerConfig?
) {

    private val clients = ConcurrentHashMap<Boolean, OkHttpClient>()
    private val imageClients = ConcurrentHashMap<Boolean, OkHttpClient>()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val cfg = activeConfig()
        val builder = request.newBuilder()
        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", USER_AGENT)
        }
        if (cfg != null && cfg.hasAuth && request.header("Authorization") == null &&
            sameOrigin(cfg.baseUrl, request.url.toString())
        ) {
            builder.header("Authorization", Credentials.basic(cfg.username, cfg.password))
        }
        chain.proceed(builder.build())
    }

    fun client(trustAllCerts: Boolean): OkHttpClient =
        clients.getOrPut(trustAllCerts) { build(trustAllCerts) }

    /** 封面专用：带磁盘缓存，避免重复拉图；OPDS feed 走无缓存 client，保证刷新生效 */
    fun imageClient(trustAllCerts: Boolean): OkHttpClient =
        imageClients.getOrPut(trustAllCerts) {
            client(trustAllCerts).newBuilder()
                .cache(Cache(File(cacheDir, "covers"), 128L * 1024 * 1024))
                .build()
        }

    private fun build(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)

        if (trustAll) {
            val trustAllManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            runCatching {
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(trustAllManager), SecureRandom())
                builder.sslSocketFactory(ctx.socketFactory, trustAllManager)
                builder.hostnameVerifier { _, _ -> true }
            }
        }
        return builder.build()
    }

    suspend fun fetch(config: ServerConfig, url: String): OpdsResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/atom+xml,application/xml;q=0.9,*/*;q=0.8")
            .apply {
                if (config.hasAuth) {
                    header("Authorization", Credentials.basic(config.username, config.password))
                }
            }
            .build()

        val response: Response = try {
            client(config.trustAllCerts).newCall(request).execute()
        } catch (e: IOException) {
            throw IOException(describeNetworkError(e), e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw OpdsHttpException(resp.code, resp.message, url)
            }
            val body = resp.body ?: throw OpdsParseException("服务端返回空响应")
            val contentType = resp.header("Content-Type")
            val serverHeader = resp.header("Server")
            val finalUrl = resp.request.url.toString()
            val feed = OpdsParser.parse(body.byteStream(), finalUrl)
            return@withContext OpdsResponse(feed, finalUrl, serverHeader, contentType)
        }
    }

    private fun describeNetworkError(e: IOException): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Failed to connect", true) || msg.contains("Connection refused", true) ->
                "无法连接服务器，请检查地址与端口、服务是否监听 0.0.0.0"
            msg.contains("timeout", true) -> "连接超时，请检查网络或服务端防火墙"
            msg.contains("Unable to resolve host", true) -> "域名解析失败，请检查地址拼写"
            msg.contains("SSL", true) || msg.contains("certificate", true) ->
                "TLS 证书校验失败，可在服务器设置中开启「信任自签证书」"
            else -> "网络错误：$msg"
        }
    }

    private fun sameOrigin(a: String, b: String): Boolean = try {
        val ua = URI(a)
        val ub = URI(b)
        ua.host.equals(ub.host, ignoreCase = true) &&
            (ua.port == ub.port || ua.port == -1 || ub.port == -1)
    } catch (e: Exception) {
        false
    }

    companion object {
        const val USER_AGENT = "CalibreReader/0.1 (Android)"
    }
}
