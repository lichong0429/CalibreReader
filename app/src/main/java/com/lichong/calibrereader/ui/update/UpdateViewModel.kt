package com.lichong.calibrereader.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichong.calibrereader.AppContainer
import com.lichong.calibrereader.BuildConfig
import com.lichong.calibrereader.data.update.ApkInstaller
import com.lichong.calibrereader.data.update.UpdateInfo
import com.lichong.calibrereader.data.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val stage: Stage = Stage.Idle,
    val info: UpdateInfo? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    /** 一次性提示（"已是最新版本"等），展示后调 clearMessage 清掉 */
    val message: String? = null,
    val error: String? = null,
    /** 已下载完但缺「安装未知应用」授权 */
    val permissionNeeded: Boolean = false,
) {
    enum class Stage { Idle, Checking, UpToDate, Available, Downloading, Downloaded, Failed }

    val hasUpdate: Boolean get() = stage == Stage.Available || stage == Stage.Downloading || stage == Stage.Downloaded

    val progressFraction: Float
        get() = if (total <= 0) 0f else (downloaded.toFloat() / total).coerceIn(0f, 1f)
}

class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    private val prefs = container.appContext
        .getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 启动时调用：距上次检查超过 12h 才真正发请求。纯静默，绝不弹窗、绝不跳转。 */
    fun checkSilent() {
        if (_state.value.stage != UpdateUiState.Stage.Idle) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        runCheck(silent = true)
    }

    fun checkManual() {
        if (_state.value.stage == UpdateUiState.Stage.Checking ||
            _state.value.stage == UpdateUiState.Stage.Downloading
        ) return
        runCheck(silent = false)
    }

    private fun runCheck(silent: Boolean) {
        _state.update {
            it.copy(
                stage = UpdateUiState.Stage.Checking,
                error = null,
                message = null,
                permissionNeeded = false
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                container.updateChecker.check(BuildConfig.VERSION_NAME)
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            result.fold(
                onSuccess = { r ->
                    when (r) {
                        is UpdateResult.UpToDate -> _state.update {
                            it.copy(
                                stage = UpdateUiState.Stage.UpToDate,
                                info = null,
                                message = if (silent) null else "已是最新版本 v${it.currentVersion}"
                            )
                        }

                        is UpdateResult.Available -> _state.update {
                            it.copy(
                                stage = UpdateUiState.Stage.Available,
                                info = r.info,
                                // 永远不自动弹窗：模态 Dialog 会吃掉整屏点击，
                                // 表现就是「所有条目都点不了」。新版本只在设置页「关于」里提示。
                                message = if (silent) null else "发现新版本 v${r.info.version}"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    // 静默检查失败不打扰用户
                    _state.update {
                        it.copy(
                            stage = UpdateUiState.Stage.Failed,
                            error = if (silent) null else (e.message ?: "检查失败")
                        )
                    }
                }
            )
        }
    }

    fun download() {
        val info = _state.value.info ?: return
        if (_state.value.stage == UpdateUiState.Stage.Downloading) return

        _state.update {
            it.copy(
                stage = UpdateUiState.Stage.Downloading,
                downloaded = 0L,
                total = info.sizeBytes,
                error = null,
                message = null
            )
        }
        viewModelScope.launch {
            runCatching {
                container.updateChecker.downloadApk(info, container.updateCacheDir) { done, total ->
                    _state.update { s -> s.copy(downloaded = done, total = if (total > 0) total else s.total) }
                }
            }.fold(
                onSuccess = { file ->
                    _state.update {
                        it.copy(
                            stage = UpdateUiState.Stage.Downloaded,
                            downloaded = file.length(),
                            total = file.length(),
                            message = "安装包已就绪，请在「关于」里点「安装」"
                        )
                    }
                    // 刻意不自动拉起安装器：自动跳转会让用户一回到 App 就被弹走，
                    // 看起来就像「界面卡住、所有条目都点不了」。安装的每一步都由用户点按钮触发。
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(stage = UpdateUiState.Stage.Failed, error = e.message ?: "下载失败")
                    }
                }
            )
        }
    }

    /** 只有用户显式点「安装 / 继续安装」才会走到这里。 */
    private fun installDownloaded() {
        val info = _state.value.info ?: return
        val ctx = container.appContext
        val apk = java.io.File(container.updateCacheDir, "CalibreReader-${info.version}.apk")
        if (ApkInstaller.needsInstallPermission(ctx)) {
            // 绝不自动跳系统的「安装未知应用」设置页 —— 那个页面看起来就像
            // 「App 卡在设置界面」，而且用户一返回又会被弹回去。只给提示 + 显式按钮。
            _state.update {
                it.copy(
                    permissionNeeded = true,
                    error = "需要先允许本应用安装 APK，请在下方点「去授权」"
                )
            }
            return
        }
        val err = ApkInstaller.install(ctx, apk)
        if (err != null) {
            _state.update { it.copy(error = err) }
        } else {
            _state.update { it.copy(permissionNeeded = false, error = null) }
        }
    }

    /** 用户显式点「安装」或授权后回来点「继续安装」。绝不自动触发。 */
    fun retryInstall() {
        if (_state.value.stage != UpdateUiState.Stage.Downloaded) return
        installDownloaded()
    }

    /** 用户显式点「去授权」才跳系统设置页。 */
    fun grantInstallPermission() {
        if (!ApkInstaller.openInstallPermissionSettings(container.appContext)) {
            _state.update { it.copy(error = "无法打开授权页，请在系统设置里搜索「安装未知应用」") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
