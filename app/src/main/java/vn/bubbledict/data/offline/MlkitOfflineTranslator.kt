package vn.bubbledict.data.offline

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.domain.OfflineTranslator
import vn.bubbledict.domain.model.OfflineModelState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BE-09 — ML Kit Translate en→vi trên máy, model tải khi user bấm (không tải ngầm).
 *
 * - `isModelReady()` hỏi trực tiếp ML Kit (`RemoteModelManager.isModelDownloaded`) — không tin
 *   DataStore, để trạng thái đúng cả khi model bị xoá từ ngoài app.
 * - Trạng thái tải ghi vào [SettingsStore] (`mlkitModelState`) cho UI 5 state của UX-03.
 * - **Hạn chế API:** ML Kit translate không có callback tiến trình tải → không có %/MB thật;
 *   UI nên hiện indeterminate (đã báo Designer/Plan). Vì vậy `mlkitModelProgress` để -1.
 * - `translate()` dùng chung nên tự đảm bảo model có mặt trước khi dịch.
 */
class MlkitOfflineTranslator(
    private val settings: SettingsStore,
) : OfflineTranslator {

    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                .build(),
        )
    }

    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }

    private val vietnameseModel: TranslateRemoteModel
        get() = TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build()

    override suspend fun isModelReady(): Boolean = runCatching {
        modelManager.isModelDownloaded(vietnameseModel).await()
    }.getOrDefault(false)

    /**
     * Tải gói en→vi. Chỉ gọi khi user bấm (spec §5.7 ràng buộc tiến trình phải hiện).
     * Trả true nếu tải xong; trạng thái được ghi vào DataStore để item Settings cập nhật.
     *
     * @param onStateChange gọi khi bắt đầu/kết thúc để UI đổi trạng thái (không phải %).
     */
    suspend fun ensureModelDownloaded(
        onStateChange: (OfflineModelState) -> Unit = {},
    ): Boolean {
        settings.setMlkitModelProgress(OfflineModelState.DOWNLOADING, -1, 0L, 0L)
        onStateChange(OfflineModelState.DOWNLOADING)
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitUnit()
            settings.setMlkitModelProgress(OfflineModelState.READY, 100, 0L, 0L)
            onStateChange(OfflineModelState.READY)
            true
        } catch (e: Exception) {
            settings.setMlkitModelProgress(OfflineModelState.FAILED, -1, 0L, 0L)
            onStateChange(OfflineModelState.FAILED)
            false
        }
    }

    /** Xoá gói offline (nút "Xoá gói" trong UX-03). */
    suspend fun deleteModel(): Boolean = runCatching {
        modelManager.deleteDownloadedModel(vietnameseModel).awaitUnit()
        settings.setMlkitModelProgress(OfflineModelState.NOT_DOWNLOADED, -1, 0L, 0L)
        true
    }.getOrDefault(false)

    override suspend fun translate(text: String): String? = runCatching {
        // Không mở dialog tải: nếu model thiếu, Task fail -> null -> pipeline trả E3a.
        translator.downloadModelIfNeeded().awaitUnit()
        translator.translate(text).await()
    }.getOrNull()

    fun close() {
        runCatching { translator.close() }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }

    private suspend fun Task<Void>.awaitUnit(): Unit = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
}