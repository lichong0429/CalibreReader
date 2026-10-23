package com.lichong.calibrereader.data.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File

/** 用 Readium 打开本地 EPUB / KEPUB 文件 */
class PublicationLoader(context: Context) {

    private val appContext = context.applicationContext

    private val retriever = AssetRetriever(appContext.contentResolver, DefaultHttpClient())
    private val opener = PublicationOpener(EpubParser())

    suspend fun open(file: File): Result<Publication> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("文件不存在或为空：${file.name}"))
        }
        val asset = retriever.retrieve(file).getOrElse { error ->
            return@withContext Result.failure(
                IllegalStateException("无法读取文件：${error.message}")
            )
        }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse { error ->
            return@withContext Result.failure(
                IllegalStateException("EPUB 解析失败：${error.message}")
            )
        }
        Result.success(publication)
    }
}
