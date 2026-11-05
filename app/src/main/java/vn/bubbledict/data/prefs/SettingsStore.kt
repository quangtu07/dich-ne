package vn.bubbledict.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vn.bubbledict.domain.model.OfflineModelState

/** Cài đặt app (DataStore). **Không** chứa API key — key ở [SecretStore]. */
data class AppSettings(
    val bubbleEnabled: Boolean = false,
    /** AC30: cạnh bong bóng "left"/"right"; rỗng = chưa từng lưu (dùng mặc định cạnh phải). */
    val bubbleEdge: String = "",
    /** AC30: tỉ lệ chiều cao 0..1 trong safe area; âm = chưa từng lưu. */
    val bubbleYRatio: Float = -1f,
    val targetLang: String = "vi",
    /** "US" (mặc định) hoặc "UK" — AC19. */
    val ttsVoice: String = "US",
    val ttsRate: Float = 1.0f,
    /** BE-17: giữ lịch sử bao nhiêu ngày. */
    val historyRetentionDays: Int = 90,
    val mlkitModelState: OfflineModelState = OfflineModelState.NOT_DOWNLOADED,
    /** -1 = không tải; 0..100 = phần trăm. */
    val mlkitModelProgress: Int = -1,
    val mlkitModelDownloadedBytes: Long = 0L,
    val mlkitModelTotalBytes: Long = 0L,
    /** Công tắc privacy: tắt = chỉ cache/ML Kit, không gửi text đi đâu. */
    val onlineSourcesEnabled: Boolean = true,
    /** Provider OpenAI-compatible (Groq mặc định) — chỉ dùng khi có key. */
    val openAiBaseUrl: String = "https://api.groq.com/openai/v1",
    val openAiModel: String = "llama-3.3-70b-versatile",
    /**
     * @deprecated AC36 (spec 1.18): MyMemory gọi **ẩn danh**, không còn lưu email liên hệ.
     *  Giữ property (luôn rỗng) để UI cũ biên dịch được; dữ liệu cũ bị [SettingsStore.purgeLegacyContactEmail] xoá.
     */
    @Deprecated("AC36: không lưu email MyMemory nữa; dùng purgeLegacyContactEmail() để xoá dữ liệu cũ")
    val myMemoryContactEmail: String = "",
    /** BE-20: ngày (yyyy-MM-dd) MyMemory đã hết quota; rỗng = chưa hết. */
    val keylessQuotaBlockedOn: String = "",
    /** F12: URL manifest cập nhật; rỗng = dùng BuildConfig.UPDATE_MANIFEST_URL. */
    val updateJsonUrl: String = "",
    /** F12: lần kiểm cập nhật gần nhất (epoch millis) — giới hạn 1 lần/ngày (AC34). */
    val lastUpdateCheckAt: Long = 0L,
    /** F12: versionCode user bấm "Bỏ qua" — không hiện banner cho bản đó nữa (AC33). */
    val skippedUpdateVersionCode: Int = 0,
)

/**
 * BUG-25: file prefs hỏng (crash giữa lúc ghi, hết chỗ) từng làm app crash `Unable to parse
 * preferences proto` và **không tự phục hồi**. Handler thay file hỏng bằng prefs rỗng để app
 * vẫn mở được; user mất cài đặt chứ không mất app.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Đọc/ghi cài đặt. `DataStore.edit` tuần tự hoá ghi nên không cần lock riêng.
 * Mọi setter là suspend; UI đọc qua [settings].
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBubbleEnabled(enabled: Boolean) = edit { it[BUBBLE_ENABLED] = enabled }

    /** AC30: chỉ lưu cạnh + tỉ lệ chiều cao, không lưu toạ độ pixel. */
    suspend fun setBubblePosition(edge: String, yRatio: Float) = edit {
        it[BUBBLE_EDGE] = if (edge == "left") "left" else "right"
        it[BUBBLE_Y_RATIO] = yRatio.coerceIn(0f, 1f)
    }

    suspend fun setTargetLang(lang: String) = edit { it[TARGET_LANG] = lang }

    /** F12: rỗng = dùng URL mặc định trong BuildConfig. */
    suspend fun setUpdateJsonUrl(url: String) = edit { it[UPDATE_JSON_URL] = url.trim() }

    suspend fun setLastUpdateCheckAt(epochMillis: Long) = edit { it[LAST_UPDATE_CHECK_AT] = epochMillis }

    suspend fun setSkippedUpdateVersionCode(versionCode: Int) = edit {
        it[SKIPPED_UPDATE_VERSION_CODE] = versionCode
    }

    /** Chỉ nhận "US"/"UK"; giá trị khác bị bỏ qua để không làm hỏng AC19. */
    suspend fun setTtsVoice(voice: String) = edit {
        val normalized = voice.uppercase()
        if (normalized == "US" || normalized == "UK") it[TTS_VOICE] = normalized
    }

    suspend fun setTtsRate(rate: Float) = edit { it[TTS_RATE] = rate.coerceIn(0.5f, 2.0f) }

    suspend fun setHistoryRetentionDays(days: Int) = edit {
        it[HISTORY_RETENTION_DAYS] = days.coerceIn(1, 3650)
    }

    suspend fun setOnlineSourcesEnabled(enabled: Boolean) = edit { it[ONLINE_SOURCES_ENABLED] = enabled }

    /**
     * @deprecated AC36: không lưu email nữa — mọi lời gọi đều xoá giá trị legacy thay vì ghi mới.
     */
    @Deprecated("AC36: không lưu email MyMemory nữa")
    suspend fun setMyMemoryContactEmail(@Suppress("UNUSED_PARAMETER") email: String) {
        purgeLegacyContactEmail()
    }

    /**
     * AC36 migration (spec 1.18): xoá email liên hệ MyMemory từng lưu ở bản ≤1.0.3.
     * Gọi 1 lần lúc mở app ([vn.bubbledict.data.Maintenance.runOnAppOpen]); idempotent, không log giá trị.
     */
    suspend fun purgeLegacyContactEmail() {
        edit { prefs -> prefs.remove(MYMEMORY_EMAIL) }
    }

    suspend fun setKeylessQuotaBlockedOn(date: String) = edit {
        it[KEYLESS_QUOTA_BLOCKED_ON] = date
    }

    suspend fun setOpenAiBaseUrl(url: String) = edit {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) it[OPENAI_BASE_URL] = trimmed
    }

    suspend fun setOpenAiModel(model: String) = edit {
        val trimmed = model.trim()
        if (trimmed.isNotEmpty()) it[OPENAI_MODEL] = trimmed
    }

    suspend fun setMlkitModelState(state: OfflineModelState) = edit { it[MLKIT_STATE] = state.name }

    suspend fun setMlkitModelProgress(
        state: OfflineModelState,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
    ) = edit {
        it[MLKIT_STATE] = state.name
        it[MLKIT_PROGRESS] = progressPercent.coerceIn(-1, 100)
        it[MLKIT_DOWNLOADED_BYTES] = downloadedBytes.coerceAtLeast(0L)
        it[MLKIT_TOTAL_BYTES] = totalBytes.coerceAtLeast(0L)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { prefs -> block(prefs) }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        bubbleEnabled = this[BUBBLE_ENABLED] ?: false,
        bubbleEdge = (this[BUBBLE_EDGE] ?: "").let { if (it == "left" || it == "right") it else "" },
        bubbleYRatio = this[BUBBLE_Y_RATIO] ?: -1f,
        targetLang = this[TARGET_LANG] ?: "vi",
        ttsVoice = (this[TTS_VOICE] ?: "US").let { if (it == "UK") "UK" else "US" },
        ttsRate = this[TTS_RATE] ?: 1.0f,
        historyRetentionDays = this[HISTORY_RETENTION_DAYS] ?: 90,
        mlkitModelState = OfflineModelState.fromName(this[MLKIT_STATE]),
        mlkitModelProgress = this[MLKIT_PROGRESS] ?: -1,
        mlkitModelDownloadedBytes = this[MLKIT_DOWNLOADED_BYTES] ?: 0L,
        mlkitModelTotalBytes = this[MLKIT_TOTAL_BYTES] ?: 0L,
        onlineSourcesEnabled = this[ONLINE_SOURCES_ENABLED] ?: true,
        openAiBaseUrl = this[OPENAI_BASE_URL] ?: "https://api.groq.com/openai/v1",
        openAiModel = this[OPENAI_MODEL] ?: "llama-3.3-70b-versatile",
        // AC36: không đọc lại email cũ; key legacy bị xoá lúc khởi động.
        myMemoryContactEmail = "",
        keylessQuotaBlockedOn = this[KEYLESS_QUOTA_BLOCKED_ON] ?: "",
        updateJsonUrl = this[UPDATE_JSON_URL] ?: "",
        lastUpdateCheckAt = this[LAST_UPDATE_CHECK_AT] ?: 0L,
        skippedUpdateVersionCode = this[SKIPPED_UPDATE_VERSION_CODE] ?: 0,
    )

    companion object {
        fun create(context: Context): SettingsStore = SettingsStore(context.applicationContext.settingsDataStore)

        val BUBBLE_ENABLED = booleanPreferencesKey("bubbleEnabled")
        val BUBBLE_EDGE = stringPreferencesKey("bubbleEdge")
        val BUBBLE_Y_RATIO = floatPreferencesKey("bubbleYRatio")
        val TARGET_LANG = stringPreferencesKey("targetLang")
        val TTS_VOICE = stringPreferencesKey("ttsVoice")
        val TTS_RATE = floatPreferencesKey("ttsRate")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("historyRetentionDays")
        val MLKIT_STATE = stringPreferencesKey("mlkitModelState")
        val MLKIT_PROGRESS = intPreferencesKey("mlkitModelProgress")
        val MLKIT_DOWNLOADED_BYTES = longPreferencesKey("mlkitModelDownloadedBytes")
        val MLKIT_TOTAL_BYTES = longPreferencesKey("mlkitModelTotalBytes")
        val ONLINE_SOURCES_ENABLED = booleanPreferencesKey("onlineSourcesEnabled")
        val OPENAI_BASE_URL = stringPreferencesKey("openAiBaseUrl")
        val OPENAI_MODEL = stringPreferencesKey("openAiModel")
        val MYMEMORY_EMAIL = stringPreferencesKey("myMemoryContactEmail")
        val KEYLESS_QUOTA_BLOCKED_ON = stringPreferencesKey("keylessQuotaBlockedOn")
        val UPDATE_JSON_URL = stringPreferencesKey("updateJsonUrl")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("lastUpdateCheckAt")
        val SKIPPED_UPDATE_VERSION_CODE = intPreferencesKey("skippedUpdateVersionCode")
    }
}