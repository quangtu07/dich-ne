package vn.bubbledict.data.offline

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.domain.LanguageDetector
import vn.bubbledict.domain.model.OfflineModelState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BE-33 / F14 (spec 1.23): quản lý **model dịch on-device theo từng ngôn ngữ** (ML Kit).
 *
 * ML Kit Translate lưu model theo **từng ngôn ngữ không phải tiếng Anh**, mỗi model dịch
 * qua/lại tiếng Anh (pivot):
 * - EN→VI cần model VI; VI→EN cần model VI; ZH→VI cần model ZH + VI (qua EN).
 * - EN không cần model (EN↔EN không cần dịch).
 *
 * Vì vậy:
 * - [requiredModels] = các ngôn ngữ (≠ EN) cần tải cho một cặp nguồn→đích.
 * - [modelState] hỏi thẳng ML Kit (không tin DataStore) — đúng cả khi model bị xoá ngoài app.
 * - Thiếu model -> pipeline trả **E30** ("Chưa tải gói ngôn ngữ này"); UI dùng lại layout S5.
 *
 * Không log text/key; chỉ log tên ngôn ngữ/kết quả tải (CR-01).
 */
class OfflineModelManager(
    private val settings: SettingsStore? = null,
    private val remoteModels: RemoteModelManager = RemoteModelManager.getInstance(),
) {

    /** Tên ngôn ngữ ML Kit (null = ngôn ngữ ngoài phạm vi 1.0.5). */
    fun translateLanguage(iso: String): String? = mlKitLanguage(iso)

    /** Model cần tải cho cặp nguồn→đích (rỗng = cả hai phía là EN, không cần model). */
    fun requiredModels(sourceLang: String, targetLang: String): List<String> =
        requiredModelsFor(sourceLang, targetLang)

    /** true = dịch offline được ngay cho cặp này (đã có đủ model). */
    suspend fun isReady(sourceLang: String, targetLang: String): Boolean {
        val required = requiredModels(sourceLang, targetLang)
        if (required.isEmpty()) return true
        return required.all { isModelDownloaded(it) }
    }

    /** Trạng thái model của một ngôn ngữ (UI Settings + màn "Gói dịch ngoại tuyến"). */
    suspend fun modelState(lang: String): OfflineModelState = when {
        mlKitLanguage(lang) == null -> OfflineModelState.NOT_DOWNLOADED
        requiredModelsFor(lang, LanguageDetector.EN).isEmpty() -> OfflineModelState.READY
        isModelDownloaded(lang) -> OfflineModelState.READY
        else -> OfflineModelState.NOT_DOWNLOADED
    }

    /**
     * Tải model cho một ngôn ngữ (chỉ khi user bấm — spec §5.7 không tải ngầm).
     * Ghi trạng thái vào [SettingsStore] cho item Settings (một trạng thái "lần tải gần nhất",
     * vì UI danh sách 5 gói hỏi [modelState] trực tiếp).
     */
    suspend fun ensureModelDownloaded(
        lang: String,
        onStateChange: (OfflineModelState) -> Unit = {},
    ): Boolean {
        if (requiredModelsFor(lang, LanguageDetector.EN).isEmpty()) {
            onStateChange(OfflineModelState.READY)
            return true
        }
        settings?.setMlkitModelProgress(OfflineModelState.DOWNLOADING, -1, 0L, 0L)
        onStateChange(OfflineModelState.DOWNLOADING)
        return try {
            val language = mlKitLanguage(lang) ?: error("unsupported language")
            // ML Kit không có API tải model rời: dùng Translator (lang → EN) rồi downloadModelIfNeeded.
            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(language)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build(),
            )
            try {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitUnit()
            } finally {
                translator.close()
            }
            settings?.setMlkitModelProgress(OfflineModelState.READY, 100, 0L, 0L)
            onStateChange(OfflineModelState.READY)
            true
        } catch (e: Exception) {
            settings?.setMlkitModelProgress(OfflineModelState.FAILED, -1, 0L, 0L)
            onStateChange(OfflineModelState.FAILED)
            false
        }
    }

    /** Xoá model của một ngôn ngữ (nút "Xoá gói" / màn gói offline). */
    suspend fun deleteModel(lang: String): Boolean = runCatching {
        val language = mlKitLanguage(lang) ?: return false
        if (requiredModelsFor(lang, LanguageDetector.EN).isEmpty()) return false
        val model = TranslateRemoteModel.Builder(language).build()
        remoteModels.deleteDownloadedModel(model).awaitUnit()
        true
    }.getOrDefault(false)

    private suspend fun isModelDownloaded(lang: String): Boolean = runCatching {
        val language = mlKitLanguage(lang) ?: return false
        val model = TranslateRemoteModel.Builder(language).build()
        remoteModels.isModelDownloaded(model).await()
    }.getOrDefault(false)

    internal companion object {
        /** ISO 639-1 -> hằng số ML Kit; null = không hỗ trợ ở 1.0.5. */
        fun mlKitLanguage(iso: String): String? = when (iso.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "vi" -> TranslateLanguage.VIETNAMESE
            "zh" -> TranslateLanguage.CHINESE
            "ko" -> TranslateLanguage.KOREAN
            "ja" -> TranslateLanguage.JAPANESE
            else -> null
        }

        /**
         * Model cần cho cặp nguồn→đích: mọi phía ≠ EN (vì ML Kit luôn pivot qua EN).
         * Ví dụ: en→vi = [vi]; zh→vi = [zh, vi]; en→en = [].
         */
        fun requiredModelsFor(sourceLang: String, targetLang: String): List<String> =
            listOf(sourceLang, targetLang)
                .map { it.lowercase() }
                .filter { it != LanguageDetector.EN && mlKitLanguage(it) != null }
                .distinct()
    }
}

/** Await một Task của Google Play Services trong coroutine (giữ tên cũ để code khác dùng lại). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

internal suspend fun Task<Void>.awaitUnit() {
    await()
}