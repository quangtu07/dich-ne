package vn.bubbledict.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import vn.bubbledict.domain.OnlineTranslator
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextKind
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cấu hình provider OpenAI-compatible (Groq/OpenRouter/Ollama...); key null = chưa cấu hình. */
data class OpenAiConfig(
    val apiKey: String?,
    val baseUrl: String,
    val model: String,
)

/**
 * Provider dùng chung cho mọi API chuẩn OpenAI `/chat/completions`:
 * **Groq free tier** (mặc định), OpenRouter, Ollama local...
 *
 * - Có key -> dùng cho WORD (JSON có nghĩa/từ loại/IPA/ví dụ) và câu/đoạn cần chất lượng cao.
 * - Không có key -> trả `NO_KEY` để chain bỏ qua (app vẫn chạy bằng MyMemory + ML Kit).
 * - Không log key/URL; lỗi map E4/E5 giống [GeminiClient].
 */
class OpenAiCompatibleTranslator(
    private val configProvider: suspend () -> OpenAiConfig,
    private val httpClient: OkHttpClient = GeminiClient.defaultHttpClient(),
    private val logger: (String) -> Unit = {},
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) : OnlineTranslator {

    override suspend fun lookup(request: LookupRequest): TranslationOutcome {
        val config = configProvider()
        val apiKey = config.apiKey?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.NO_KEY)

        val isWord = request.kind == TextKind.WORD
        val body = OpenAiChatRequest(
            model = config.model,
            messages = listOf(
                OpenAiMessage(
                    role = "user",
                    content = if (isWord) wordPrompt(request.text) else translatePrompt(request.text),
                ),
            ),
            temperature = 0.2,
            responseFormat = if (isWord) OpenAiResponseFormat("json_object") else null,
        )

        val response = try {
            execute(config, body)
        } catch (e: InterruptedIOException) {
            logger("openai timeout")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.TIMEOUT)
        } catch (e: IOException) {
            logger("openai io ${e.javaClass.simpleName}")
            return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
        }

        response.use { r ->
            if (!r.isSuccessful) {
                logger("openai http ${r.code}")
                return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.HTTP, httpStatus = r.code)
            }
            val content = extractContent(r.body?.string().orEmpty())
                ?: run {
                    logger("openai empty")
                    return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY, r.code)
                }
            return if (isWord) parseWord(content, r.code) else parseTranslation(content, r.code)
        }
    }

    private fun parseWord(content: String, status: Int): TranslationOutcome {
        val cleaned = content.trim().removeCodeFence()
        val payload = runCatching { json.decodeFromString<WordPayload>(cleaned) }.getOrNull()
            ?: run {
                logger("openai parse word failed")
                return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.PARSE, status)
            }
        if (payload.meaning.isBlank()) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY, status)
        }
        if (!looksVietnamese(payload.meaning)) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_VIETNAMESE, status)
        }
        return TranslationOutcome.Word(payload, provider = PROVIDER_NAME)
    }

    private fun parseTranslation(content: String, status: Int): TranslationOutcome {
        val text = content.trim().removeCodeFence().trim()
        if (text.isEmpty()) return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.EMPTY, status)
        if (!looksVietnamese(text)) {
            return TranslationOutcome.Failure(HttpErrors.E5, FailureReason.NOT_VIETNAMESE, status)
        }
        return TranslationOutcome.Translation(text, provider = PROVIDER_NAME)
    }

    private fun extractContent(rawBody: String): String? {
        val parsed = runCatching { json.decodeFromString<OpenAiChatResponse>(rawBody) }.getOrNull()
            ?: return null
        return parsed.choices.firstOrNull()?.message?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun looksVietnamese(text: String): Boolean =
        text.any { it.lowercaseChar() in VIETNAMESE_CHARS }

    private fun wordPrompt(word: String) = buildString {
        append("You are an English–Vietnamese dictionary. For the English word: \"")
        append(word)
        append("\" return ONLY a JSON object with fields: meaning (short Vietnamese meaning with full diacritics), ")
        append("partOfSpeech (n/v/adj/adv), ipa (IPA of the English word), ")
        append("examples (array of {\"text\": English sentence, \"translation\": Vietnamese translation}), ")
        append("synonyms (array of English words). No markdown, no extra text.")
    }

    private fun translatePrompt(text: String) = buildString {
        append("Translate this English text into Vietnamese. ")
        append("Keep every sentence (same sentence count), preserve meaning, do not shorten. ")
        append("Return only the Vietnamese translation with full diacritics.\nText: ")
        append(text)
    }

    private suspend fun execute(config: OpenAiConfig, body: OpenAiChatRequest): Response {
        val url = "${config.baseUrl.trimEnd('/')}/chat/completions".toHttpUrl()
        val requestBody = json.encodeToString(OpenAiChatRequest.serializer(), body)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
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

    @Serializable
    private data class OpenAiChatRequest(
        val model: String,
        val messages: List<OpenAiMessage>,
        val temperature: Double? = null,
        @SerialName("response_format") val responseFormat: OpenAiResponseFormat? = null,
    )

    @Serializable
    private data class OpenAiMessage(val role: String, val content: String)

    @Serializable
    private data class OpenAiResponseFormat(val type: String)

    @Serializable
    private data class OpenAiChatResponse(val choices: List<OpenAiChoice> = emptyList())

    @Serializable
    private data class OpenAiChoice(val message: OpenAiMessage? = null)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val PROVIDER_NAME = "openai-compatible"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val VIETNAMESE_CHARS =
            ("ăâđêôơư" +
                "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ").toSet()

        /** Model hay trả JSON trong ```json fences — bóc ra trước khi parse. */
        private fun String.removeCodeFence(): String {
            val trimmed = trim()
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed.removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()
        }
    }
}