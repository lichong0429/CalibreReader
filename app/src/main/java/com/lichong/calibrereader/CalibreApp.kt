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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
