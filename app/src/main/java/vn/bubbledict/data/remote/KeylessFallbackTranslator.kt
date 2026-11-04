package vn.bubbledict.data.remote

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.bubbledict.domain.OnlineTranslator
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.LookupRequest as LookupRequest2

/**
 * Nguồn dịch keyless **dự phòng** khi MyMemory hết lượt (Chief chốt, research 2026-11-04).
 *
 * Mozhi là proxy tình nguyện đứng trên engine Google; 2 instance còn sống đã probe thật:
 * - `mozhi.ducks.party` (chính), `mozhi.adminforge.de` (dự phòng) — EN→VI tốt, 1–3s.
 * - Lingva chết toàn bộ, SimplyTranslate trả rỗng → **không dùng**.
 *
 * Hợp đồng: `GET /api/translate?engine=google&from=en&to=vi&text=…`, đoạn dài thì
 * `POST /api/translate` form-urlencoded cùng field; kết quả lấy trường `translated-text`.
 *
 * Chỉ dùng cho câu/đoạn: WORD trả `NOT_APPLICABLE` (dịch máy từ đơn sai nghĩa, xem MyMemory).
 * Không log URL/text (CR-01).
 */
class MozhiTranslator(
    private val instanceUrls: List<String> = DEFAULT_INSTANCES,
    private val httpClient: OkHttpClient = defaultClient(),
    private val logger: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : OnlineTranslator {

    /**
     * Research 2026-11-04: instance chết fail nhanh (connection refused 0,3s) nhưng instance sống
     * chậm (tới 2,4s) -> nhớ instance vừa hỏng 5 phút trong RAM để lần sau khỏi thử lại.
     */
    private val instanceDownUntil = ConcurrentHashMap<String, Long>()

    override suspend fun lookup(request: LookupRequest2): TranslationOutcome {
        if (instanceUrls.isEmpty()) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_APPLICABLE)
        }
        if (request.kind == TextKind.WORD) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_APPLICABLE)
        }

        // Spec 1.26: Mozhi (engine Google) không nhận mã ISO "zh" trần -> map sang zh-CN.
        val sourceLang = providerLang(request.sourceLang.ifBlank { "en" })
        val targetLang = providerLang(request.targetLang.ifBlank { "vi" })
        val translated = StringBuilder()
        for (chunk in splitForQuery(request.text)) {
            when (val outcome = lookupChunkWithFailover(chunk, sourceLang, targetLang)) {
                is TranslationOutcome.Translation -> {
                    if (translated.isNotEmpty()) translated.append(' ')
                    translated.append(outcome.text)
                }

                is TranslationOutcome.Failure -> return outcome
                else -> {}
            }
        }
        val text = translated.toString().trim()
        if (text.isEmpty()) return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY)
        return TranslationOutcome.Translation(text = text, provider = PROVIDER)
    }

    /** Thử từng instance theo thứ tự; instance lỗi thì sang instance kế tiếp. */
    private suspend fun lookupChunkWithFailover(
        chunk: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationOutcome {
        var lastFailure: TranslationOutcome.Failure? = null
        val currentTime = now()
        for ((index, base) in instanceUrls.withIndex()) {
            if ((instanceDownUntil[base] ?: 0L) > currentTime) {
                logger("mozhi instance-${index + 1} skipped (recently failed)")
                continue
            }
            when (val outcome = lookupChunk(base, chunk, sourceLang, targetLang)) {
                is TranslationOutcome.Translation -> {
                    instanceDownUntil.remove(base)
                    return outcome
                }

                is TranslationOutcome.Failure -> {
                    lastFailure = outcome
                    // 5xx/IO coi như instance hỏng tạm thời; 429 cũng nghỉ 5 phút cho đỡ spam.
                    if (outcome.reason in TRANSIENT_FAILURES) {
                        instanceDownUntil[base] = currentTime + INSTANCE_DOWN_CACHE_MS
                    }
                    logger("mozhi instance-${index + 1} failed reason=${outcome.reason}")
                }

                else -> {}
            }
        }
        return lastFailure ?: TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
    }

    private suspend fun lookupChunk(
        baseUrl: String,
        chunk: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationOutcome =
        withContext(Dispatchers.IO) {
            try {
                val request = if (chunk.length <= MAX_GET_CHARS) {
                    Request.Builder()
                        .url(buildGetUrl(baseUrl, chunk, sourceLang, targetLang))
                        .header("Accept", "application/json")
                        .build()
                } else {
                    // Đoạn dài: GET dễ vượt giới hạn URL -> POST form cùng field (research đã thử được).
                    Request.Builder()
                        .url("$baseUrl/api/translate")
                        .header("Accept", "application/json")
                        .post(
                            FormBody.Builder()
                                .add("engine", ENGINE)
                                .add("from", sourceLang)
                                .add("to", targetLang)
                                .add("text", chunk)
                                .build(),
                        )
                        .build()
                }
                httpClient.newCall(request).execute().use { response ->
                    val status = response.code
                    // Research §9: Mozhi KHÔNG có quota riêng; upstream 429 bị bọc thành 500.
                    // 429/5xx ở đây = instance đang hỏng/quá tải -> UNAVAILABLE, không phải "hết lượt".
                    // Phân loại gom một chỗ ở [SourceFailure] (CR-04).
                    if (!response.isSuccessful) {
                        // 400/422 = provider từ chối cặp/mã ngôn ngữ -> PAIR_UNSUPPORTED (E31).
                        if (status == 400 || status == 422) {
                            return@withContext TranslationOutcome.Failure(
                                HttpErrors.E4,
                                FailureReason.PAIR_UNSUPPORTED,
                                httpStatus = status,
                            )
                        }
                        return@withContext SourceFailure.fromHttpStatus(status, hasQuota = false)
                    }
                    val body = response.body?.string().orEmpty()
                    val translated = parseTranslation(body)
                    if (translated.isNullOrBlank()) {
                        TranslationOutcome.Failure(HttpErrors.E5, FailureReason.PARSE)
                    } else {
                        TranslationOutcome.Translation(text = translated.trim(), provider = PROVIDER)
                    }
                }
            } catch (e: IOException) {
                logger("mozhi io-${e.javaClass.simpleName}")
                TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
            } catch (e: Exception) {
                logger("mozhi error-${e.javaClass.simpleName}")
                TranslationOutcome.Failure(HttpErrors.E5, FailureReason.PARSE)
            }
        }

    /** Bảng mã riêng của Mozhi: `zh` -> `zh-CN` (spec 1.26); ngôn ngữ khác dùng mã ISO. */
    internal fun providerLang(iso: String): String = ProviderLanguages.mozhi(iso)

    private fun buildGetUrl(baseUrl: String, text: String, sourceLang: String, targetLang: String): String {
        val encoded = java.net.URLEncoder.encode(text, "UTF-8")
        return "$baseUrl/api/translate?engine=$ENGINE&from=$sourceLang&to=$targetLang&text=$encoded"
    }

    /** Lấy `translated-text` (có gạch nối) hoặc biến thể; null = không đọc được. */
    internal fun parseTranslation(body: String): String? {
        for (key in RESULT_KEYS) {
            val match = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)
            if (match != null) {
                return match.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }
        return null
    }

    /** Chia theo ranh giới câu; mỗi chunk ≤ [MAX_CHUNK_CHARS] (research: 1.680 ký tự vẫn dịch đủ). */
    internal fun splitForQuery(text: String): List<String> {
        if (text.length <= MAX_CHUNK_CHARS) return listOf(text)
        val chunks = mutableListOf<String>()
        val builder = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
            if (builder.isNotEmpty() && builder.length + sentence.length + 1 > MAX_CHUNK_CHARS) {
                chunks += builder.toString()
                builder.clear()
            }
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(sentence)
        }
        if (builder.isNotEmpty()) chunks += builder.toString()
        return chunks
    }

    companion object {
        const val PROVIDER = "mozhi"

        /** Instance vừa lỗi thì bỏ qua trong 5 phút (RAM, reset khi process chết). */
        const val INSTANCE_DOWN_CACHE_MS = 5L * 60L * 1000L

        /** Lỗi coi là "instance hỏng tạm thời" để cache; PARSE/EMPTY thì không cache. */
        private val TRANSIENT_FAILURES = setOf(
            FailureReason.IO,
            FailureReason.HTTP,
            FailureReason.UNAVAILABLE,
            FailureReason.QUOTA,
            FailureReason.TIMEOUT,
        )

        /** Giới hạn GET để URL không quá dài; dài hơn thì POST form. */
        const val MAX_GET_CHARS = 350

        /** Chunk tối đa mỗi request; dài hơn thì tách theo câu. */
        const val MAX_CHUNK_CHARS = 1800

        private const val ENGINE = "google"
        private val RESULT_KEYS = listOf("translated-text", "translatedText", "translation")

        /** Instance đã probe sống (research 2026-11-04); thứ tự = thứ tự thử. */
        val DEFAULT_INSTANCES = listOf(
            "https://mozhi.ducks.party",
            "https://mozhi.adminforge.de",
        )

        /** Mỗi instance tối đa ~8s; tổng budget cả chain do ChainedTranslator giữ 10s (spec E4). */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}