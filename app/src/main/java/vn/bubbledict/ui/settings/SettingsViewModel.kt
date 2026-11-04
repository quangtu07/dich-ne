package vn.bubbledict.ui.settings

import android.app.Application
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.bubbledict.BubbleDictApp
import vn.bubbledict.R
import vn.bubbledict.data.prefs.AppSettings
import vn.bubbledict.data.remote.GeminiClient
import vn.bubbledict.data.remote.KeyCheckResult
import vn.bubbledict.domain.model.OfflineModelState
import vn.bubbledict.platform.BubbleController
import vn.bubbledict.ui.result.Voice

/** Thông điệp một lần cho snackbar; map sang chuỗi ở UI. */
enum class SettingsMessage(@param:StringRes val textRes: Int) {
    API_KEY_SAVED(R.string.api_key_saved_toast),
    API_KEY_SAVE_FAILED(R.string.api_key_save_failed),
    API_KEY_DELETED(R.string.api_key_deleted_toast),
    API_KEY_MISSING(R.string.api_key_empty_error),
    PROVIDER_SAVED(R.string.provider_saved_toast),
    BUBBLE_NEEDS_OVERLAY(R.string.bubble_need_overlay),
    BUBBLE_NEEDS_NOTIFICATION(R.string.bubble_need_notification),
    CONTACT_EMAIL_SAVED(R.string.contact_email_saved_toast),
    MODEL_DOWNLOAD_FAILED(R.string.model_download_failed),
}

/**
 * FE-05 — ViewModel tab Cài đặt. Nối thật `SettingsStore` (DataStore) + `SecretStore`
 * (EncryptedSharedPreferences) của BE-06; quyền overlay/thông báo do UI đọc từ hệ thống
 * rồi phủ lên state (VM không giữ Context quyền runtime).
 *
 * Chưa nối: kiểm tra key (BE-08), tải model (BE-09), dung lượng cache (BE-07/BE-17).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as BubbleDictApp).container
    private val settingsStore = container.settingsStore
    private val secretStore = container.secretStore
    private val offlineTranslator = container.offlineTranslator
    private val versionLabel: String = versionTag(application)

    /** Tăng mỗi lần key đổi để state combine đọc lại SecretStore (API đồng bộ). */
    private val keyRevision = MutableStateFlow(0)
    private val keyTest = MutableStateFlow(ApiKeyTestStatus.IDLE)

    /** Dung lượng cache thật (byte), lấy qua `Maintenance.cacheSizeBytes()` (BE-17). */
    private val cacheBytes = MutableStateFlow(0L)
    /** F14: trạng thái gói ngoại tuyến 5 ngôn ngữ (hỏi thẳng ML Kit, không tin DataStore). */
    private val modelStates = MutableStateFlow<Map<String, OfflineModelState>>(emptyMap())

    private val _messages = MutableSharedFlow<SettingsMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<SettingsMessage> = _messages

    val state: StateFlow<SettingsUiState> = combine(
        settingsStore.settings,
        keyRevision,
        keyTest,
        cacheBytes,
        modelStates,
    ) { settings, _, test, bytes, models ->
        settings.toUiState(test, bytes, models)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            versionLabel = versionLabel,
            apiKeyStatus = if (secretStore.geminiApiKey() != null) ApiKeyStatus.SAVED else ApiKeyStatus.MISSING,
        ),
    )

    init {
        refreshCacheBytes()
        refreshModelStates()
    }

    /** F14: đọc trạng thái 5 gói; chưa xác định được thì để NOT_DOWNLOADED (UI hiện "—"). */
    fun refreshModelStates() {
        viewModelScope.launch {
            val manager = container.offlineModelManager
            val result = mutableMapOf<String, OfflineModelState>()
            LANGUAGE_CODES.forEach { lang ->
                result[lang] = runCatching { manager.modelState(lang) }
                    .getOrDefault(OfflineModelState.NOT_DOWNLOADED)
            }
            modelStates.value = result
        }
    }

    /** F14: tải 1 gói (chỉ khi user bấm — không tải ngầm). */
    fun downloadModelFor(lang: String) {
        viewModelScope.launch {
            modelStates.value = modelStates.value + (lang to OfflineModelState.DOWNLOADING)
            runCatching { container.offlineModelManager.ensureModelDownloaded(lang) }
            refreshModelStates()
        }
    }

    /** F14: tải tuần tự các gói còn thiếu (tránh 4 model ML Kit tải song song). */
    fun downloadAllModels() {
        viewModelScope.launch {
            val manager = container.offlineModelManager
            LANGUAGE_CODES.filter { it != "en" }.forEach { lang ->
                val current = runCatching { manager.modelState(lang) }
                    .getOrDefault(OfflineModelState.NOT_DOWNLOADED)
                if (current != OfflineModelState.READY) {
                    modelStates.value = modelStates.value + (lang to OfflineModelState.DOWNLOADING)
                    runCatching { manager.ensureModelDownloaded(lang) }
                    refreshModelStates()
                }
            }
        }
    }

    fun deleteModelFor(lang: String) {
        viewModelScope.launch {
            runCatching { container.offlineModelManager.deleteModel(lang) }
            refreshModelStates()
        }
    }

    /** F14: đổi ngôn ngữ đích (DataStore), mặc định "vi". */
    fun setTargetLang(lang: String) {
        viewModelScope.launch {
            settingsStore.setTargetLang(lang)
            refreshModelStates()
        }
    }

    /** Cache đổi khi tra cứu/xoá; đọc lại dung lượng thật, không hardcode. */
    fun refreshCacheBytes() {
        viewModelScope.launch { readCacheBytes() }
    }

    private suspend fun readCacheBytes() {
        cacheBytes.value = runCatching { container.maintenance.cacheSizeBytes() }.getOrDefault(0L)
    }

    /** AC12: xoá lịch sử tra cứu, giữ từ vựng + cache (mỗi thứ có nút riêng). */
    fun deleteHistory() {
        viewModelScope.launch {
            container.maintenance.clearHistory()
            readCacheBytes()
        }
    }

    /** AC12: xoá từ vựng qua `Maintenance.clearVocab()` (một đường, có test BE-17). */
    fun deleteVocab() {
        viewModelScope.launch {
            container.maintenance.clearVocab()
        }
    }

    fun deleteCache() {
        viewModelScope.launch {
            container.maintenance.clearCache()
            readCacheBytes()
        }
    }

    /**
     * F2 (P3): bật/tắt bong bóng thật qua [BubbleController] — không chỉ lưu DataStore.
     * Chỉ lưu `bubbleEnabled=true` khi service đã chạy; thiếu quyền thì báo để UI mở màn quyền.
     */
    fun setBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                container.bubbleController.stop()
                settingsStore.setBubbleEnabled(false)
                return@launch
            }
            when (container.bubbleController.start()) {
                BubbleController.StartResult.STARTED,
                BubbleController.StartResult.ALREADY_RUNNING,
                -> settingsStore.setBubbleEnabled(true)

                BubbleController.StartResult.NEEDS_OVERLAY_PERMISSION ->
                    _messages.emit(SettingsMessage.BUBBLE_NEEDS_OVERLAY)

                BubbleController.StartResult.NEEDS_NOTIFICATION_PERMISSION ->
                    _messages.emit(SettingsMessage.BUBBLE_NEEDS_NOTIFICATION)
            }
        }
    }

    /** Service không tự sống lại sau reboot/kill: mở lại app mà setting đang bật thì bật lại. */
    fun restoreBubbleIfEnabled() {
        viewModelScope.launch {
            if (settingsStore.current().bubbleEnabled &&
                !container.bubbleController.isRunning()
            ) {
                container.bubbleController.start()
            }
        }
    }

    /** F11: công tắc "Cho phép nguồn online" (mặc định BẬT). */
    fun setOnlineSourcesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setOnlineSourcesEnabled(enabled) }
    }

    fun setVoice(voice: Voice) {
        viewModelScope.launch { settingsStore.setTtsVoice(voice.name) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            val saved = secretStore.saveGeminiApiKey(key)
            keyTest.value = ApiKeyTestStatus.IDLE
            keyRevision.value += 1
            _messages.emit(if (saved) SettingsMessage.API_KEY_SAVED else SettingsMessage.API_KEY_SAVE_FAILED)
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            secretStore.clear()
            keyTest.value = ApiKeyTestStatus.IDLE
            keyRevision.value += 1
            _messages.emit(SettingsMessage.API_KEY_DELETED)
        }
    }

    /** AC27: email liên hệ MyMemory (để trống = xoá, quay về 5k/ngày ẩn danh). */
    fun saveMyMemoryContactEmail(email: String) {
        viewModelScope.launch {
            settingsStore.setMyMemoryContactEmail(email)
            _messages.emit(SettingsMessage.CONTACT_EMAIL_SAVED)
        }
    }

    /** F11: key cho provider OpenAI-compatible (Groq/OpenRouter/Ollama), chỉ dùng khi có. */
    fun saveOpenAiKey(key: String) {
        viewModelScope.launch {
            val saved = secretStore.saveOpenAiApiKey(key)
            keyRevision.value += 1
            _messages.emit(if (saved) SettingsMessage.PROVIDER_SAVED else SettingsMessage.API_KEY_SAVE_FAILED)
        }
    }

    fun saveOpenAiConfig(baseUrl: String, model: String) {
        viewModelScope.launch {
            if (baseUrl.isNotBlank()) settingsStore.setOpenAiBaseUrl(baseUrl.trim())
            if (model.isNotBlank()) settingsStore.setOpenAiModel(model.trim())
            _messages.emit(SettingsMessage.PROVIDER_SAVED)
        }
    }

    /** Gọi thật `GeminiClient.checkKey()` (BE-08) với key vừa nhập hoặc key đã lưu; không lưu key. */
    fun testApiKey(input: String) {
        val candidate = input.trim().ifBlank { secretStore.geminiApiKey().orEmpty() }
        if (candidate.isBlank()) {
            viewModelScope.launch { _messages.emit(SettingsMessage.API_KEY_MISSING) }
            return
        }
        keyTest.value = ApiKeyTestStatus.CHECKING
        viewModelScope.launch {
            val client = GeminiClient(apiKeyProvider = { candidate })
            keyTest.value = when (client.checkKey()) {
                KeyCheckResult.OK -> ApiKeyTestStatus.OK
                KeyCheckResult.MISSING -> ApiKeyTestStatus.IDLE
                KeyCheckResult.INVALID,
                KeyCheckResult.QUOTA,
                KeyCheckResult.UNREACHABLE,
                -> ApiKeyTestStatus.FAILED
            }
        }
    }

    /** Tải gói ML Kit thật (BE-09); trạng thái DOWNLOADING/READY/FAILED chảy qua DataStore. */
    fun requestModelDownload() {
        viewModelScope.launch {
            val ok = offlineTranslator.ensureModelDownloaded()
            if (!ok) _messages.emit(SettingsMessage.MODEL_DOWNLOAD_FAILED)
        }
    }

    fun deleteModel() {
        viewModelScope.launch { offlineTranslator.deleteModel() }
    }

    private fun AppSettings.toUiState(
        testStatus: ApiKeyTestStatus,
        cacheBytes: Long,
        models: Map<String, OfflineModelState>,
    ): SettingsUiState {
        val key = secretStore.geminiApiKey()
        return SettingsUiState(
            bubbleEnabled = bubbleEnabled,
            onlineSourcesEnabled = onlineSourcesEnabled,
            targetLang = targetLang,
            modelStates = models,
            modelRequiredForPair = runCatching {
                container.offlineModelManager.requiredModels("en", targetLang)
            }.getOrDefault(emptyList()),
            freeSourceStatus = when {
                !onlineSourcesEnabled -> FreeSourceStatus.OFF
                // BE-20 đánh dấu ngày (yyyy-MM-dd) đã hết quota -> tới hết ngày bỏ qua MyMemory.
                keylessQuotaBlockedOn == java.time.LocalDate.now().toString() ->
                    FreeSourceStatus.QUOTA_EXHAUSTED

                else -> FreeSourceStatus.ON
            },
            apiKeyStatus = if (key != null) ApiKeyStatus.SAVED else ApiKeyStatus.MISSING,
            apiKeyLast4 = key?.takeLast(4),
            apiKeyTest = testStatus,
            openAiKeyStatus = if (secretStore.openAiApiKey() != null) {
                ApiKeyStatus.SAVED
            } else {
                ApiKeyStatus.MISSING
            },
            openAiKeyLast4 = secretStore.openAiApiKey()?.takeLast(4),
            myMemoryContactEmail = myMemoryContactEmail,
            openAiBaseUrl = openAiBaseUrl,
            openAiModel = openAiModel,
            voice = if (ttsVoice.equals("UK", ignoreCase = true)) Voice.UK else Voice.US,
            ukAvailable = true,
            model = ModelDownloadUi(
                status = mlkitModelState.toUiStatus(),
                progressPercent = mlkitModelProgress.coerceIn(0, 100),
                downloadedLabel = formatMb(mlkitModelDownloadedBytes),
                totalLabel = if (mlkitModelTotalBytes > 0) formatMb(mlkitModelTotalBytes) else "30",
            ),
            cacheSizeLabel = formatSize(cacheBytes),
            versionLabel = versionLabel,
        )
    }

    private fun OfflineModelState.toUiStatus(): ModelDownloadStatus = when (this) {
        OfflineModelState.NOT_DOWNLOADED -> ModelDownloadStatus.NOT_DOWNLOADED
        OfflineModelState.DOWNLOADING -> ModelDownloadStatus.DOWNLOADING
        OfflineModelState.READY -> ModelDownloadStatus.READY
        OfflineModelState.FAILED -> ModelDownloadStatus.FAILED
        OfflineModelState.PAUSED_NETWORK -> ModelDownloadStatus.NOT_DOWNLOADED
    }

    /** < 1 MB hiện KB (tránh "0,0 MB" khi cache có vài KB); 0 hiện "0 KB". */
    private fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "0 KB"
        bytes >= 1024L * 1024L -> formatMb(bytes) + " MB"
        bytes >= 1024L -> formatMbKb(bytes) + " KB"
        else -> "< 1 KB"
    }

    private fun formatMbKb(bytes: Long): String =
        String.format(Locale("vi", "VN"), "%.1f", bytes / 1024.0)

    private fun formatMb(bytes: Long): String {
        if (bytes <= 0L) return "0"
        return String.format(Locale("vi", "VN"), "%.1f", bytes / (1024.0 * 1024.0))
    }

    private fun versionTag(application: Application): String = runCatching {
        val info = application.packageManager.getPackageInfo(application.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    }.getOrDefault("?")
}

/** 5 ngôn ngữ hỗ trợ F14 (mã ISO 639-1). */
private val LANGUAGE_CODES = listOf("vi", "en", "zh", "ko", "ja")
