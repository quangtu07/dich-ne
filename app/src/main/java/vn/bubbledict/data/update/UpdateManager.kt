package vn.bubbledict.data.update

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.bubbledict.data.prefs.SettingsStore

/**
 * F12 — điều phối banner cập nhật: kiểm manifest (tối đa 1 lần/ngày) → tải APK → xác thực
 * SHA-256 → mở trình cài. Không cài ngầm: chỉ mở installer để user bấm tiếp.
 *
 * UI đọc [state], gọi [checkIfDue] khi mở app, [downloadAndInstall] khi user bấm Cập nhật,
 * [skipCurrentVersion] khi bấm Bỏ qua, và [openInstallPermissionSettings] khi gặp `U5`.
 */
class UpdateManager(
    private val context: Context,
    private val checker: UpdateChecker,
    private val client: OkHttpClient,
    private val installer: ApkInstaller,
    private val settingsStore: SettingsStore,
    /** URL mặc định khi Settings chưa đặt `updateJsonUrl`. */
    private val defaultManifestUrl: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(val versionName: String, val notes: String) : State
        data class Downloading(val percent: Int) : State
        data object InstallerOpened : State
        data class Failed(val code: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var manifest: UpdateManifest? = null

    /**
     * Kiểm cập nhật nếu đã quá 1 ngày (AC34). Mất mạng/lỗi thì vào [State.Failed] nhưng UI
     * được phép bỏ qua im lặng (E24).
     *
     * @param force true khi user bấm "Kiểm tra cập nhật" — bỏ qua giới hạn 1 lần/ngày.
     */
    suspend fun check(force: Boolean = false) {
        if (_state.value is State.Checking || _state.value is State.Downloading) return

        val settings = settingsStore.current()
        val nowMillis = now()
        if (!force && !UpdatePolicy.shouldCheck(nowMillis, settings.lastUpdateCheckAt)) return

        // Ghi mốc ngay cả khi lỗi để không spam mạng mỗi lần mở app.
        settingsStore.setLastUpdateCheckAt(nowMillis)

        _state.value = State.Checking
        val url = settings.updateJsonUrl.ifBlank { defaultManifestUrl }
        when (val result = checker.check(url)) {
            is UpdateChecker.Result.Available -> {
                if (!UpdatePolicy.shouldShowBanner(
                        manifestVersionCode = result.manifest.versionCode,
                        currentVersionCode = checker.currentVersionCode,
                        skippedVersionCode = settings.skippedUpdateVersionCode,
                    )
                ) {
                    manifest = null
                    _state.value = State.Idle
                    return
                }
                manifest = result.manifest
                _state.value = State.Available(
                    versionName = result.manifest.versionName.ifBlank { "bản mới" },
                    notes = result.manifest.notes,
                )
            }

            UpdateChecker.Result.UpToDate -> {
                manifest = null
                _state.value = State.Idle
            }

            is UpdateChecker.Result.Failed -> {
                manifest = null
                _state.value = State.Failed(result.code)
            }
        }
    }

    /** AC33: user bấm "Bỏ qua" — lưu versionCode để không hiện banner cho bản này nữa. */
    suspend fun skipCurrent() {
        manifest?.let { settingsStore.setSkippedUpdateVersionCode(it.versionCode) }
        manifest = null
        _state.value = State.Idle
    }

    /**
     * Tải APK của bản mới, xác thực SHA-256 rồi mở trình cài.
     * @return true nếu đã mở được trình cài; false kèm [state] = Failed(code).
     */
    suspend fun downloadAndInstall(): Boolean {
        val target = manifest ?: return fail(UpdateErrorCodes.BAD_MANIFEST)
        if (!installer.canInstallPackages()) return fail(UpdateErrorCodes.INSTALL_PERMISSION)

        // Dọn bản tải dở của lần trước trước khi tải bản mới.
        purgeDownloadedApks()
        val apkFile = File(context.cacheDir, "$APK_PREFIX${target.versionCode}.apk")
        val downloaded = runCatching { download(target, apkFile) }.getOrElse { false }
        if (!downloaded) {
            apkFile.delete()
            return fail(UpdateErrorCodes.DOWNLOAD)
        }

        val actual = runCatching {
            apkFile.inputStream().use { input ->
                UpdateDigest.sha256Hex(read = { buf -> input.read(buf) })
            }
        }.getOrNull()
        if (actual == null || !UpdateDigest.matches(target.sha256, actual)) {
            // E26: checksum sai -> không mở installer, xoá file.
            apkFile.delete()
            return fail(UpdateErrorCodes.CHECKSUM)
        }

        val intent = installer.installIntent(apkFile)
        if (intent == null) {
            apkFile.delete()
            return fail(UpdateErrorCodes.DOWNLOAD)
        }
        manifest = null
        _state.value = State.InstallerOpened
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "cannot open installer: ${it.javaClass.simpleName}")
            fail(UpdateErrorCodes.DOWNLOAD)
        }
    }

    /** Mở màn cấp quyền "cài từ nguồn không xác định". */
    fun openInstallPermissionSettings(): Boolean = runCatching {
        context.startActivity(installer.unknownSourcesIntent())
        true
    }.getOrDefault(false)

    /**
     * BUG-21 (spec §5.9 "xoá sau khi cài"): dọn APK cập nhật đã tải ở lượt trước.
     * Gọi lúc mở app (sau khi installer đã dùng xong file) — không xoá ngay sau `startActivity`
     * vì trình cài hệ thống có thể còn đọc file. Idempotent, best-effort, không throw.
     */
    fun purgeDownloadedApks() {
        runCatching {
            context.cacheDir
                ?.listFiles { f -> f.isFile && f.name.startsWith(APK_PREFIX) && f.name.endsWith(".apk") }
                ?.forEach { stale ->
                    if (stale.delete()) Log.d(TAG, "purged ${stale.name}") else Log.w(TAG, "cannot purge ${stale.name}")
                }
        }.onFailure { Log.w(TAG, "purge apks failed: ${it.javaClass.simpleName}") }
    }

    fun resetError() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }

    /** Alias cho [check] — tên cũ dùng trong tài liệu BE-26. */
    suspend fun checkIfDue(force: Boolean = false) = check(force)

    private suspend fun download(target: UpdateManifest, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            val request = Request.Builder().url(target.apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            written += n
                            if (total > 0) {
                                val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _state.value = State.Downloading(percent)
                                }
                            }
                        }
                        output.flush()
                        return@withContext written > 0
                    }
                }
            }
        }

    private fun fail(code: String): Boolean {
        _state.value = State.Failed(code)
        return false
    }

    private companion object {
        const val TAG = "UpdateManager"

        /** Tiền tố file APK tải về trong cacheDir — dùng cho [purgeDownloadedApks] (BUG-21). */
        const val APK_PREFIX = "update-"
    }
}