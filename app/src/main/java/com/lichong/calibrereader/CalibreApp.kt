package com.lichong.calibrereader

import android.app.Application
import android.content.Context
import com.lichong.calibrereader.data.db.AppDatabase
import com.lichong.calibrereader.data.db.BookRepository
import com.lichong.calibrereader.data.download.BookDownloader
import com.lichong.calibrereader.data.image.CoverLoader
import com.lichong.calibrereader.data.net.OpdsClient
import com.lichong.calibrereader.data.net.ServerProbe
import com.lichong.calibrereader.data.prefs.ServerStore
import com.lichong.calibrereader.data.reader.PublicationLoader
import com.lichong.calibrereader.data.sync.ProgressSync
import com.lichong.calibrereader.data.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(private val app: Application) {

    val appContext: Context get() = app

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val serverStore = ServerStore(app)

    val database = AppDatabase.build(app)

    val bookRepo = BookRepository(database.bookDao())

    val opdsClient = OpdsClient(app.cacheDir) { serverStore.current.value }

    val serverProbe = ServerProbe(opdsClient)

    val downloader = BookDownloader(app, opdsClient)

    val covers = CoverLoader(app, opdsClient) { serverStore.current.value }

    val publicationLoader = PublicationLoader(app)

    val progressSync = ProgressSync(opdsClient)

    /**
     * 检查更新专用的客户端：**不带任何认证拦截器**。
     * 绝不能复用 opdsClient —— 那套会附加 Basic 认证，把服务器账号密码发到 api.github.com。
     */
    private val updateClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    val updateChecker = UpdateChecker(updateClient)

    val updateCacheDir: File get() = File(app.cacheDir, "update")

    fun bootstrap() {
        appScope.launch { serverStore.primeCache() }
    }
}

class CalibreApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }
}
