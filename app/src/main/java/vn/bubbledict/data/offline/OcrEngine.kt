package vn.bubbledict.data.offline

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * BE-15 — OCR on-device bằng ML Kit Text Recognition (Latin bundled, không upload ảnh).
 *
 * - Resize cạnh dài về ≤ [MAX_LONG_SIDE] để tránh ảnh lớn (E12) và giữ tốc độ.
 * - Ảnh đen/không có chữ -> [OcrResult.Empty] (E9/E10: "không đọc được vùng này").
 * - Quá [RECOGNIZE_TIMEOUT_MS] hoặc lỗi init -> [OcrResult.Error] với mã **E23** (AC31):
 *   bong bóng dừng vòng quay, UI báo lỗi và cho chọn lại, không treo im lặng.
 */
class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    sealed interface OcrResult {
        data class Text(val text: String) : OcrResult
        data object Empty : OcrResult
        data class Error(val code: String) : OcrResult
    }

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val prepared = runCatching { downscale(bitmap) }
            .getOrElse { return OcrResult.Error(E23) }
        var timedOut = false
        return try {
            val result = withTimeoutOrNull(RECOGNIZE_TIMEOUT_MS) {
                // QA-only (debug hook): giả lập OCR chậm để chụp card AC31(a); release luôn 0.
                if (debugArtificialDelayMs > 0L) delay(debugArtificialDelayMs)
                val image = InputImage.fromBitmap(prepared, 0)
                val text = suspendCancellableCoroutine { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            if (continuation.isActive) continuation.resume(result.text)
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume("")
                        }
                }
                val normalized = normalizeLines(text)
                if (normalized.isBlank()) OcrResult.Empty else OcrResult.Text(normalized)
            }
            if (result == null) {
                timedOut = true
                OcrResult.Error(E23)
            } else {
                result
            }
        } catch (e: Exception) {
            OcrResult.Error(E23)
        } finally {
            // Timeout: ML Kit có thể vẫn đang đọc bitmap -> không recycle để tránh crash.
            if (prepared !== bitmap && !timedOut) prepared.recycle()
        }
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    /** Gộp dòng bị ngắt giữa câu, giữ đoạn cách nhau 1 dòng trống. */
    private fun normalizeLines(raw: String): String = raw
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** @return bitmap đã thu nhỏ để cạnh dài ≤ [MAX_LONG_SIDE]; test E12 gọi trực tiếp. */
    internal fun downscale(bitmap: Bitmap): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= MAX_LONG_SIDE) return bitmap
        val scale = MAX_LONG_SIDE.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        const val MAX_LONG_SIDE = 2000

        /** AC31/E23: quá 10 giây coi như thất bại, không để vòng quay chạy mãi. */
        const val RECOGNIZE_TIMEOUT_MS = 10_000L
        const val E23 = "E23"

        /**
         * Hook QA (chỉ debug build có UI set): >0 thì OCR trễ thêm ngần này ms.
         * Dùng để chụp card "Đang nhận dạng chữ…" (AC31a) và kiểm E23/timeout 10s.
         */
        @Volatile
        @JvmStatic
        var debugArtificialDelayMs: Long = 0L
    }
}