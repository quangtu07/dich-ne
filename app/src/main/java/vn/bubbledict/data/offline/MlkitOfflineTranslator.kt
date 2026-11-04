package vn.bubbledict.data.offline

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.domain.LanguageDetector
import vn.bubbledict.domain.OfflineTranslator
import vn.bubbledict.domain.model.OfflineModelState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BE-09 + F14 (BE-34): ML Kit Translate **theo cặp ngôn ngữ** trên máy.
 *
 * - Model theo từng ngôn ngữ ≠ EN (ML Kit pivot qua EN) — quản lý ở [OfflineModelManager].
 * - `src != EN` → dịch `src → EN` trước, rồi `EN → tgt` (nếu `tgt != EN`); `src == tgt` trả nguyên văn.
 * - `isModelReady(pair)` = đủ model theo [OfflineModelManager]; thiếu -> pipeline trả E30/E3c.
 * - Không tải ngầm: chỉ tải trong [ensureModelDownloaded] khi user bấm (spec §5.7).
 * - Không log text/key; chỉ log tên ngôn ngữ/kết quả tải (CR-01).
 */
class MlkitOfflineTranslator(
    private val settings: SettingsStore,
    private val modelManager: OfflineModelManager = OfflineModelManager(settings),
) : OfflineTranslator {

    /** Cache translator theo cặp "src>tgt" — ML Kit khuyến nghị tái dùng client. */
    private val translators = ConcurrentHashMap<String, Translator>()

    override suspend fun isModelReady(sourceLang: String, targetLang: String): Boolean =
        modelManager.isReady(sourceLang, targetLang)

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? = runCatching {
        val source = sourceLang.lowercase()
        val target = targetLang.lowercase()
        if (source == target) return text

        var current = text
        // Pivot qua EN: model ML Kit luôn dịch giữa (một ngôn ngữ) và EN.
        if (source != LanguageDetector.EN) {
            current = translatorFor(source, LanguageDetector.EN).translate(current).await()
        }
        if (target != LanguageDetector.EN) {
            current = translatorFor(LanguageDetector.EN, target).translate(current).await()
        }
        current
    }.getOrNull()

    /**
     * Tải gói cho **một ngôn ngữ** (UI danh sách 5 gói của UX-06 gọi [modelState] trực tiếp).
     * Giữ chữ ký cũ cho SettingsViewModel: mặc định tải gói tiếng Việt.
     */
    suspend fun ensureModelDownloaded(
        lang: String = LanguageDetector.VI,
        onStateChange: (OfflineModelState) -> Unit = {},
    ): Boolean = modelManager.ensureModelDownloaded(lang, onStateChange)

    /** Xoá gói của một ngôn ngữ (mặc định VI, giữ tương thích UI cũ). */
    suspend fun deleteModel(lang: String = LanguageDetector.VI): Boolean {
        translators.remove("$lang>en")?.close()
        translators.remove("en>$lang")?.close()
        val deleted = modelManager.deleteModel(lang)
        if (lang == LanguageDetector.VI) {
            settings.setMlkitModelProgress(OfflineModelState.NOT_DOWNLOADED, -1, 0L, 0L)
        }
        return deleted
    }

    /** Trạng thái model của một ngôn ngữ cho item Settings/màn gói offline. */
    suspend fun modelState(lang: String): OfflineModelState = modelManager.modelState(lang)

    fun close() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
    }

    private fun translatorFor(sourceIso: String, targetIso: String): Translator {
        val key = "$sourceIso>$targetIso"
        return translators.getOrPut(key) {
            val source = OfflineModelManager.mlKitLanguage(sourceIso) ?: TranslateLanguage.ENGLISH
            val target = OfflineModelManager.mlKitLanguage(targetIso) ?: TranslateLanguage.VIETNAMESE
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build(),
            )
        }
    }

    internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
}