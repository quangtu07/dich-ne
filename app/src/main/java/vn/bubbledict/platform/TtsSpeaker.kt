package vn.bubbledict.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import vn.bubbledict.domain.model.TtsVoice
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * BE-12 — phát âm tiếng Anh bằng TTS hệ thống (F10).
 *
 * - Giọng `US` (mặc định) / `UK`; thiếu giọng yêu cầu thì **vẫn đọc bằng giọng còn lại** và báo E14
 *   (spec §5.8: "nếu có en-US thì vẫn đọc được bằng giọng đó"); thiếu cả hai mới không đọc (E14).
 * - Đang đọc mà bấm lần nữa -> caller gọi [stop] (AC22); lỗi giữa lúc đọc -> E15, không crash.
 * - Text dài hơn `maxSpeechInputLength` -> chia đoạn theo khoảng trắng và đọc lần lượt; chỉ cắt
 *   thật khi một "từ" dài hơn giới hạn (E16, `truncated = true`).
 * - Không gọi API, không ghi lịch sử, không đổi kết quả dịch (F10).
 */
class TtsSpeaker(context: Context) : AutoCloseable {

    /** Kết quả một lượt đọc, đủ cho UI + mã lỗi spec §5.8. */
    data class Outcome(
        val completed: Boolean,
        /** "E14" thiếu giọng, "E15" lỗi giữa lúc đọc; null = ổn hoặc user tự dừng. */
        val errorCode: String? = null,
        /** Đã đọc bằng giọng khác vì giọng yêu cầu không có (E14 nhưng vẫn nghe được). */
        val spokenWithFallback: Boolean = false,
        /** E16: text bị cắt bớt vì quá dài cho TTS. */
        val truncated: Boolean = false,
        val spokenVoice: TtsVoice? = null,
    )

    private val initResult = CompletableDeferred<Boolean>()
    private val progressListener = ProgressListener()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        initResult.complete(status == TextToSpeech.SUCCESS)
    }.apply {
        setOnUtteranceProgressListener(progressListener)
    }

    /** Có giọng cho [voice] trên máy không (để UI bật/tắt segmented US|UK). */
    suspend fun isLanguageAvailable(voice: TtsVoice): Boolean {
        if (!awaitReady()) return false
        return isLocaleAvailable(localeOf(voice))
    }

    suspend fun usAvailable(): Boolean = isLanguageAvailable(TtsVoice.US)

    suspend fun ukAvailable(): Boolean = isLanguageAvailable(TtsVoice.UK)

    /**
     * F14/BE-35 (UX-06 §4): app chỉ có giọng tiếng Anh (US/UK) -> nguồn ≠ EN thì **ẩn nút Nghe
     * im lặng**, không hiện dòng "chưa có giọng", không tooltip.
     */
    suspend fun isAvailableFor(sourceLang: String): Boolean =
        if (sourceLang.equals("en", ignoreCase = true)) usAvailable() || ukAvailable() else false

    /**
     * Đọc [text] bằng [voice]; suspend tới khi đọc xong, bị [stop], hoặc lỗi.
     * Gọi lại khi đang đọc: caller nên [stop] trước (AC22).
     */
    suspend fun speak(text: String, voice: TtsVoice, rate: Float = 1.0f): Outcome {
        if (text.isBlank()) return Outcome(completed = false, errorCode = "E15")
        if (!awaitReady()) return Outcome(completed = false, errorCode = "E15")

        var spokenVoice = voice
        var fallback = false
        if (!isLocaleAvailable(localeOf(voice))) {
            val other = if (voice == TtsVoice.US) TtsVoice.UK else TtsVoice.US
            if (!isLocaleAvailable(localeOf(other))) {
                return Outcome(completed = false, errorCode = "E14")
            }
            spokenVoice = other
            fallback = true
        }

        val locale = localeOf(spokenVoice)
        val languageResult = tts.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return Outcome(completed = false, errorCode = "E14")
        }
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))

        val max = maxSpeechLength()
        val (chunks, truncated) = splitForTts(text, max)

        for (chunk in chunks) {
            when (val error = speakChunk(chunk)) {
                null -> Unit // xong đoạn này
                STOPPED -> return Outcome(
                    completed = false,
                    truncated = truncated,
                    spokenWithFallback = fallback,
                    spokenVoice = spokenVoice,
                )
                else -> {
                    safeStop()
                    return Outcome(
                        completed = false,
                        errorCode = error,
                        truncated = truncated,
                        spokenWithFallback = fallback,
                        spokenVoice = spokenVoice,
                    )
                }
            }
        }
        return Outcome(
            completed = true,
            spokenWithFallback = fallback,
            truncated = truncated,
            spokenVoice = spokenVoice,
        )
    }

    /** AC22: dừng ngay, không crash kể cả khi TTS chưa init xong. */
    fun stop() {
        safeStop()
        pending.values.toList().forEach { it.complete(STOPPED) }
        pending.clear()
    }

    fun isSpeaking(): Boolean = runCatching { tts.isSpeaking }.getOrDefault(false)

    override fun close() {
        safeStop()
        runCatching { tts.shutdown() }
        pending.values.toList().forEach { it.complete(STOPPED) }
        pending.clear()
    }

    /** null = đọc xong; [STOPPED] = bị dừng; "E15" = lỗi. */
    private suspend fun speakChunk(chunk: String): String? {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        pending[id] = deferred

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                pending.remove(id)
                safeStop()
            }
            deferred.invokeOnCompletion { cause ->
                pending.remove(id)
                if (!continuation.isActive) return@invokeOnCompletion
                if (cause == null) {
                    continuation.resume(deferred.getCompleted())
                } else {
                    continuation.resume("E15")
                }
            }
            val queued = runCatching {
                tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id)
            }.getOrDefault(TextToSpeech.ERROR)
            if (queued == TextToSpeech.ERROR) {
                deferred.complete("E15")
            }
        }
    }

    private suspend fun awaitReady(): Boolean =
        runCatching { initResult.await() }.getOrDefault(false)

    private fun isLocaleAvailable(locale: Locale): Boolean = runCatching {
        when (tts.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
            -> true
            else -> false
        }
    }.getOrDefault(false)

    private fun maxSpeechLength(): Int =
        runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)

    /** Chia text theo khoảng trắng ≤ max; cờ truncated chỉ true khi phải cắt giữa "từ". */
    private fun splitForTts(text: String, max: Int): Pair<List<String>, Boolean> {
        if (text.length <= max) return listOf(text) to false
        val chunks = mutableListOf<String>()
        var rest = text
        var truncated = false
        while (rest.isNotEmpty()) {
            if (rest.length <= max) {
                chunks += rest
                break
            }
            val cut = rest.lastIndexOf(' ', startIndex = max - 1)
            if (cut > 0) {
                chunks += rest.substring(0, cut).trim()
                rest = rest.substring(cut + 1).trim()
            } else {
                // Một "từ" dài hơn giới hạn: buộc phải cắt (E16)
                chunks += rest.substring(0, max).trim()
                rest = rest.substring(max).trim()
                truncated = true
            }
        }
        return chunks.filter { it.isNotEmpty() } to truncated
    }

    private fun safeStop() {
        runCatching { tts.stop() }
    }

    private inner class ProgressListener : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { pending[it]?.complete(null) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            utteranceId?.let { pending[it]?.complete("E15") }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let { pending[it]?.complete("E15") }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let { pending[it]?.complete(STOPPED) }
        }
    }

    private fun localeOf(voice: TtsVoice): Locale =
        if (voice == TtsVoice.UK) Locale.UK else Locale.US

    private companion object {
        const val STOPPED = "STOPPED"
    }
}