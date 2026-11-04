package vn.bubbledict.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import vn.bubbledict.domain.OnlineTranslator
import vn.bubbledict.domain.model.LookupRequest as Request2
import vn.bubbledict.domain.model.TextKind
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Nguồn dịch **không cần API key** cho câu/đoạn (chốt Chief + spec 1.5 §5.9 F11).
 *
 * MyMemory (`api.mymemory.translated.net`) — nghiên cứu thực tế:
 * - Miễn phí 5.000 ký tự/ngày/IP, gọi **ẩn danh**: spec 1.17 §5.9 cấm gửi email liên hệ (`de=`).
 * - Giới hạn **500 bytes/request** -> request dài được chia theo byte UTF-8 (≤[MAX_QUERY_BYTES]).
 * - `WORD` **không** đi MyMemory (từ đơn sai nghĩa, không IPA/ví dụ) -> `NOT_APPLICABLE`.
 * - `quotaFinished` (HTTP 200) / 429 -> `QUOTA` = hết lượt thật (E19), **không** báo "cần mạng".
 * - 5xx / IO / timeout -> `UNAVAILABLE` = nguồn trục trặc tạm thời (BUG-16) — không dán nhãn hết lượt.
 */
class MyMemoryTranslator(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** BE-20: hết quota thì bỏ qua MyMemory cho tới hết ngày (không gọi lại vô ích). */
    private val isQuotaBlockedToday: suspend () -> Boolean = { false },
    private val markQuotaExhausted: suspend () -> Unit = {},
    private val logger: (String) -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : OnlineTranslator {

    override suspend fun lookup(request: Request2): TranslationOutcome {
        // BUG-17: `WORD` không thuộc phạm vi MyMemory (dịch máy từ đơn sai nghĩa) -> phải trả
        // NOT_APPLICABLE **trước** khi kiểm quota, để tra từ không bị dán nhãn E19 khi hết lượt;
        // chain sẽ bỏ qua và pipeline rơi xuống ML Kit (hoặc E3a nếu chưa tải gói offline).
        if (request.kind == TextKind.WORD) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_APPLICABLE)
        }
        if (isQuotaBlockedToday()) {
            logger("mymemory quota-blocked-today")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.QUOTA, httpStatus = 429)
        }

        val chunks = splitForQuery(request.text)
        val translated = mutableListOf<String>()

        val sourceLang = ProviderLanguages.myMemory(request.sourceLang.ifBlank { "en" })
        val targetLang = ProviderLanguages.myMemory(request.targetLang.ifBlank { "vi" })
        for (chunk in chunks) {
            when (val outcome = lookupChunk(chunk, sourceLang, targetLang)) {
                is TranslationOutcome.Translation -> translated += outcome.text
                is TranslationOutcome.Failure -> {
                    if (outcome.reason == FailureReason.QUOTA) markQuotaExhausted()
                    return outcome
                }
                is TranslationOutcome.Word ->
                    return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.PARSE)
            }
        }

        val text = translated.joinToString(separator = " ").trim()
        if (text.isEmpty()) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY)
        }
        // E22 chỉ áp cho đích tiếng Việt; các đích khác chấp nhận (không có bộ kiểm script).
        if (TargetLanguages.isVietnamese(targetLang) && !looksVietnamese(text)) {
            logger("mymemory not-target-language")
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_VIETNAMESE)
        }
        return TranslationOutcome.Translation(text, provider = PROVIDER_NAME)
    }

    private suspend fun lookupChunk(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationOutcome {
        val builder = "$baseUrl/get"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", text)
            .addQueryParameter("langpair", "$sourceLang|$targetLang")
        // Privacy (spec 1.17 §5.9): KHÔNG gửi `de=<email>` — mọi request MyMemory đều ẩn danh.

        val response = try {
            execute(Request.Builder().url(builder.build()).get().build())
        } catch (e: InterruptedIOException) {
            logger("mymemory timeout")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.TIMEOUT)
        } catch (e: IOException) {
            logger("mymemory io ${e.javaClass.simpleName}")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
        }

        response.use { r ->
            val status = r.code
            // BUG-16 (research §9): MyMemory KHÔNG báo hết quota bằng status. 429 = hết lượt thật;
            // 5xx = server/keyless trục trặc tạm thời -> UNAVAILABLE, không dán nhãn "hết lượt".
            // Phân loại gom một chỗ ở [SourceFailure] (CR-04) để không tái phát ở client khác.
            if (!r.isSuccessful) {
                logger("mymemory http $status")
                return SourceFailure.fromHttpStatus(status, hasQuota = true)
            }
            val body = runCatching {
                json.decodeFromString<MyMemoryResponse>(r.body?.string().orEmpty())
            }.getOrNull()
            if (body == null) {
                logger("mymemory parse-failed")
                return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.PARSE)
            }
            // Spec 1.16 §5.9: quota của MyMemory có thể nằm trong body (`responseStatus=429`) dù HTTP 200.
            if (body.responseStatus == 429) {
                logger("mymemory quota-body 429")
                return SourceFailure.quota(429)
            }
            if (body.quotaFinished == true) {
                logger("mymemory quota-finished")
                return SourceFailure.quota(429)
            }
            val translatedText = body.responseData?.translatedText?.trim().orEmpty()
            // Query vượt 500 byte/request: lỗi phía mình, không phải hết lượt (spec 1.16: E2/E28).
            if (translatedText.startsWith("QUERY LENGTH LIMIT EXCEEDED", ignoreCase = true)) {
                logger("mymemory query-too-long")
                return SourceFailure.unavailable()
            }
            if (translatedText.isEmpty()) {
                return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY)
            }
            // MyMemory trả nguyên văn khi không có bản dịch (memory match) -> không dùng được.
            if (translatedText.equals(text, ignoreCase = true)) {
                logger("mymemory unchanged")
                return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_VIETNAMESE)
            }
            return TranslationOutcome.Translation(translatedText, provider = PROVIDER_NAME)
        }
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = httpClient.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private fun looksVietnamese(text: String): Boolean =
        text.any { it.lowercaseChar() in VIETNAMESE_CHARS }

    /** Chia theo **byte UTF-8** (giới hạn 500 bytes/request), ưu tiên cắt ở dấu câu rồi khoảng trắng. */
    private fun splitForQuery(text: String): List<String> {
        if (text.toByteArray(Charsets.UTF_8).size <= MAX_QUERY_BYTES) return listOf(text)
        val chunks = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (rest.toByteArray(Charsets.UTF_8).size <= MAX_QUERY_BYTES) {
                chunks += rest
                break
            }
            var end = minOf(rest.length, MAX_QUERY_BYTES)
            while (end > 1 && rest.substring(0, end).toByteArray(Charsets.UTF_8).size > MAX_QUERY_BYTES) {
                end--
            }
            val window = rest.substring(0, end)
            val sentenceCut = maxOf(window.lastIndexOf(". "), window.lastIndexOf('\n'))
            val spaceCut = window.lastIndexOf(' ')
            val cut = when {
                sentenceCut > 0 -> sentenceCut + 1
                spaceCut > 0 -> spaceCut
                else -> end
            }
            chunks += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        return chunks.filter { it.isNotEmpty() }
    }

    @Serializable
    private data class MyMemoryResponse(
        @SerialName("responseData") val responseData: MyMemoryData? = null,
        @SerialName("quotaFinished") val quotaFinished: Boolean? = null,
        @SerialName("responseStatus") val responseStatus: Int? = null,
    )

    @Serializable
    private data class MyMemoryData(
        @SerialName("translatedText") val translatedText: String? = null,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.mymemory.translated.net"
        const val PROVIDER_NAME = "mymemory"

        /** BE-19: timeout 4 giây/request (nguồn công cộng, không để treo lâu). */
        const val TIMEOUT_SECONDS = 4L

        fun defaultHttpClient(): OkHttpClient = GeminiClient.defaultHttpClient()
            .newBuilder()
            .callTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        /** Giới hạn thật là 500 bytes/request; chừa biên an toàn. */
        const val MAX_QUERY_BYTES = 450

        private val VIETNAMESE_CHARS =
            ("ăâđêôơư" +
                "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ").toSet()
    }
}