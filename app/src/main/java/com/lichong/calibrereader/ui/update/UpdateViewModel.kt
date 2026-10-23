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
    /** 启动时静默发现新版才弹的对话框 */
    val promptVisible: Boolean = false,
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

    /** 启动时调用：距上次检查超过 12h 才真正发请求，且只在有新版时弹窗 */
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
                                promptVisible = silent,
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
                message = null,
                promptVisible = false
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
                            message = "下载完成，准备安装"
                        )
                    }
                    installDownloaded()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(stage = UpdateUiState.Stage.Failed, error = e.message ?: "下载失败")
                    }
                }
            )
        }
    }

    private fun installDownloaded() {
        val info = _state.value.info ?: return
        val ctx = container.appContext
        val apk = java.io.File(container.updateCacheDir, "CalibreReader-${info.version}.apk")
        if (ApkInstaller.needsInstallPermission(ctx)) {
            _state.update { it.copy(permissionNeeded = true) }
            ApkInstaller.openInstallPermissionSettings(ctx)
            return
        }
        val err = ApkInstaller.install(ctx, apk)
        if (err != null) {
            _state.update { it.copy(error = err) }
        } else {
            _state.update { it.copy(permissionNeeded = false) }
        }
    }

    /** 从授权页返回后重试安装 */
    fun retryInstall() {
        if (_state.value.stage != UpdateUiState.Stage.Downloaded) return
        installDownloaded()
    }

    fun dismissPrompt() = _state.update { it.copy(promptVisible = false) }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
