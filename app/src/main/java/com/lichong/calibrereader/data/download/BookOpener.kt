package com.lichong.calibrereader.data.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object BookOpener {

    fun mimeOf(extension: String): String = when (extension.lowercase()) {
        "epub", "kepub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/x-mobi8-ebook"
        "cbz" -> "application/vnd.comicbook+zip"
        "cbr" -> "application/vnd.comicbook-rar"
        "fb2" -> "application/fb2+zip"
        "txt" -> "text/plain"
        "html", "htm" -> "text/html"
        else -> "*/*"
    }

    /** 返回 null 表示成功，否则为错误提示 */
    fun open(context: Context, file: File): String? {
        if (!file.exists() || file.length() == 0L) return "文件不存在或已损坏"
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeOf(file.extension))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "打开《${file.nameWithoutExtension}》")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        } catch (e: ActivityNotFoundException) {
            "没有能打开 ${file.extension.uppercase()} 的应用"
        } catch (e: Exception) {
            e.message ?: "打开失败"
        }
    }
}
