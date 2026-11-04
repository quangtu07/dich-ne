package vn.bubbledict.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import vn.bubbledict.domain.OnlineTranslator
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client Gemini `generateContent` (BE-08), model `gemini-2.5-flash-lite`.
 *
 * - WORD: `responseSchema` JSON -> [WordPayload] (nghĩa, từ loại, IPA, ví dụ, đồng nghĩa).
 * - SENTENCE/PARAGRAPH: dịch **một lần cho cả đoạn**, trả văn bản, không cắt còn 1 câu.
 * - Timeout 10 giây; 429/5xx/timeout -> [HttpErrors.E4]; rỗng/không phải tiếng Việt -> [HttpErrors.E5].
 * - **Không log URL, không log key, không log text user**: [logger] chỉ nhận mã/sự kiện ngắn.
 * - Lỗi mạng (UnknownHost...) trả [FailureReason.IO] để pipeline chuyển sang ML Kit offline (E3).
 */
/** Kết quả "Kiểm tra key" cho Settings (FE-05). */
enum class KeyCheckResult { OK, MISSING, INVALID, QUOTA, UNREACHABLE }

class GeminiClient(
    private val apiKeyProvider: () -> String?,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val model: String = DEFAULT_MODEL,
    private val logger: (String) -> Unit = {},
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) : OnlineTranslator {

    override suspend fun lookup(request: LookupRequest): TranslationOutcome {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.NO_KEY)

        val body = buildRequest(request)
        val response = try {
            execute(buildUrl(key), body)
        } catch (e: InterruptedIOException) {
            // Gồm SocketTimeoutException và callTimeout của OkHttp
            logger("gemini timeout")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.TIMEOUT)
        } catch (e: IOException) {
            logger("gemini io ${e.javaClass.simpleName}")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
        }

        response.use { r ->
            if (!r.isSuccessful) {
                logger("gemini http ${r.code}")
                return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.HTTP, httpStatus = r.code)
            }
            val raw = r.body?.string().orEmpty()
            val text = extractText(raw)
                ?: run {
                    logger("gemini empty")
                    return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY, r.code)
                }

            return when (request.kind) {
                TextKind.WORD -> parseWord(text, r.code)
                TextKind.SENTENCE, TextKind.PARAGRAPH -> parseTranslation(text, r.code)
            }
        }
    }

    /**
     * "Kiểm tra key" ở Settings (UX-03 §3.4): gọi 1 request tối thiểu rồi phân loại kết quả.
     * Không log key, không ghi history/cache.
     */
    suspend fun checkKey(): KeyCheckResult {
        apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return KeyCheckResult.MISSING
        return when (val outcome = lookup(LookupRequest("hello", TextSource.SELECTION))) {
            is TranslationOutcome.Word, is TranslationOutcome.Translation -> KeyCheckResult.OK
            is TranslationOutcome.Failure -> when {
                outcome.reason == FailureReason.NO_KEY -> KeyCheckResult.MISSING
                outcome.reason == FailureReason.HTTP && outcome.httpStatus in INVALID_KEY_STATUSES ->
                    KeyCheckResult.INVALID
                outcome.reason == FailureReason.HTTP && outcome.httpStatus == 429 -> KeyCheckResult.QUOTA
                outcome.reason == FailureReason.TIMEOUT || outcome.reason == FailureReason.IO ->
                    KeyCheckResult.UNREACHABLE
                // Key hợp lệ nhưng model trả rỗng/không phải tiếng Việt: key vẫn dùng được.
                else -> KeyCheckResult.OK
            }
        }
    }

    fun looksVietnamese(text: String): Boolean = text.any { it.lowercaseChar() in VIETNAMESE_CHARS }

    private fun parseWord(candidateText: String, status: Int): TranslationOutcome {
        val payload = runCatching { json.decodeFromString<WordPayload>(candidateText) }.getOrNull()
            ?: run {
                logger("gemini parse word failed")
                return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.PARSE, status)
            }
        if (payload.meaning.isBlank()) {
            logger("gemini word empty")
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY, status)
        }
        if (!looksVietnamese(payload.meaning)) {
            logger("gemini word not-vi")
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_VIETNAMESE, status)
        }
        return TranslationOutcome.Word(payload, provider = PROVIDER_NAME)
    }

    private fun parseTranslation(candidateText: String, status: Int): TranslationOutcome {
        val text = candidateText.trim()
        if (text.isEmpty()) {
            logger("gemini translation empty")
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY, status)
        }
        if (!looksVietnamese(text)) {
            logger("gemini translation not-vi")
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_VIETNAMESE, status)
        }
        return TranslationOutcome.Translation(text, provider = PROVIDER_NAME)
    }

    private fun buildUrl(key: String): HttpUrl =
        "$baseUrl/v1beta/models/$model:generateContent"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", key)
            .build()

    private fun buildRequest(request: LookupRequest) = when (request.kind) {
        TextKind.WORD -> GeminiGenerateRequest(
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(wordPrompt(request.text))))),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.2,
                responseMimeType = "application/json",
                responseSchema = wordResponseSchema(),
            ),
        )
        TextKind.SENTENCE -> GeminiGenerateRequest(
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(sentencePrompt(request.text))))),
            generationConfig = GeminiGenerationConfig(temperature = 0.2),
        )
        TextKind.PARAGRAPH -> GeminiGenerateRequest(
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(paragraphPrompt(request.text))))),
            generationConfig = GeminiGenerationConfig(temperature = 0.2),
        )
    }

    private fun wordPrompt(word: String) = buildString {
        append("You are an English–Vietnamese dictionary. For the English word: \"")
        append(word)
        append("\" return JSON only with: meaning (short Vietnamese meaning with full diacritics), ")
        append("partOfSpeech (n/v/adj/adv), ipa (IPA of the English word), ")
        append("examples (2-3 English sentences with Vietnamese translations), synonyms (English words).")
    }

    private fun sentencePrompt(sentence: String) = buildString {
        append("Translate this English sentence into natural Vietnamese. ")
        append("Return only the Vietnamese translation with full diacritics; keep the meaning, do not shorten.\n")
        append("Text: ")
        append(sentence)
    }

    private fun paragraphPrompt(paragraph: String) = buildString {
        append("Translate this whole English paragraph into Vietnamese in one pass. ")
        append("Keep every sentence (same sentence count), preserve meaning, do not shorten and do not translate sentence by sentence incorrectly. ")
        append("Return only the Vietnamese translation with full diacritics.\n")
        append("Text: ")
        append(paragraph)
    }

    /** Đọc `candidates[0].content.parts[*].text`, nối lại; null nếu rỗng. */
    private fun extractText(rawBody: String): String? {
        val parsed = runCatching { json.decodeFromString<GeminiGenerateResponse>(rawBody) }.getOrNull()
            ?: return null
        val text = parsed.candidates.firstOrNull()
            ?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString(separator = "")
            ?.trim()
        return text?.takeIf { it.isNotEmpty() }
    }

    private suspend fun execute(url: HttpUrl, body: GeminiGenerateRequest): Response {
        val requestBody = json.encodeToString(GeminiGenerateRequest.serializer(), body)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val call = httpClient.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            })
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = "gemini-2.5-flash-lite"
        const val PROVIDER_NAME = "gemini"
        const val TIMEOUT_SECONDS = 10L

        private val INVALID_KEY_STATUSES = setOf(400, 401, 403)

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Heuristic: chữ Việt có dấu. Từ mượn không dấu ("video") có thể bị coi là không phải tiếng Việt. */
        private val VIETNAMESE_CHARS =
            ("ăâđêôơư" +
                "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ").toSet()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}