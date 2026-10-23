package com.lichong.calibrereader.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lichong.calibrereader.BuildConfig
import java.io.File

/** 拉起系统安装器。APK 放 cacheDir，靠已有的 FileProvider（cache-path）共享。 */
object ApkInstaller {

    private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** Android 8+ 需要用户单独授权「安装未知应用」 */
    fun needsInstallPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()

    /** 跳到本应用的「安装未知应用」授权页。跳转失败返回 false。 */
    fun openInstallPermissionSettings(ctx: Context): Boolean = try {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: ActivityNotFoundException) {
        // 部分 ROM 没有该页面，退回应用详情页
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e2: Exception) {
            false
        }
    }

    /** 返回 null 表示已成功拉起安装器，否则是给用户看的错误提示。 */
    fun install(ctx: Context, apk: File): String? {
        if (!apk.exists() || apk.length() == 0L) return "安装包不存在，请重新下载"
        if (apk.extension.lowercase() != "apk") return "文件格式不对：${apk.name}"

        val uri: Uri = try {
            FileProvider.getUriForFile(ctx, AUTHORITY, apk)
        } catch (e: IllegalArgumentException) {
            return "无法共享安装包：${e.message.orEmpty()}"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "系统没有可用的安装器"
        } catch (e: Exception) {
            e.message ?: "拉起安装器失败"
        }
    }
}
