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
import vn.bubbledict.domain.DictionaryEnricher
import vn.bubbledict.domain.DictionaryInfo
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Nguồn từ điển **không cần key**: `api.dictionaryapi.dev` (chốt Chief — spike thật 04-11:
 * trả IPA + định nghĩa tiếng Anh + synonyms; ví dụ "ubiquitous" -> /juːˈbɪk.wə.təs/).
 *
 * - Chỉ dùng cho **WORD**, chỉ gửi đúng từ đang tra (không gửi câu/đoạn), không gửi ảnh.
 * - Bổ sung IPA/định nghĩa bên cạnh nghĩa tiếng Việt từ ML Kit khi user **không có key**.
 * - 404/parse lỗi/mạng lỗi -> null; pipeline vẫn trả kết quả ML Kit bình thường.
 */
class FreeDictionaryApi(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val logger: (String) -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : DictionaryEnricher {

    override suspend fun enrich(word: String): DictionaryInfo? {
        val cleaned = word.trim()
        if (cleaned.isEmpty() || cleaned.contains(' ')) return null

        val url = "$baseUrl/api/v2/entries/en/${cleaned.lowercase()}".toHttpUrl()
        val response = try {
            execute(Request.Builder().url(url).get().build())
        } catch (e: IOException) {
            logger("dictionary io ${e.javaClass.simpleName}")
            return null
        }

        response.use { r ->
            if (!r.isSuccessful) {
                logger("dictionary http ${r.code}")
                return null
            }
            val entries = runCatching {
                json.decodeFromString<List<DictionaryEntry>>(r.body?.string().orEmpty())
            }.getOrNull() ?: return null

            val entry = entries.firstOrNull() ?: return null
            val ipa = entry.phonetic?.takeIf { it.isNotBlank() }
                ?: entry.phonetics.firstOrNull { !it.text.isNullOrBlank() }?.text
            val primary = entry.meanings.firstOrNull()
            val definitions = entry.meanings
                .flatMap { meaning -> meaning.definitions.mapNotNull { it.definition } }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_DEFINITIONS)
            val synonyms = primary?.synonyms.orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_SYNONYMS)

            if (ipa == null && definitions.isEmpty()) {
                logger("dictionary empty")
                return null
            }
            return DictionaryInfo(
                word = entry.word ?: cleaned,
                ipa = ipa,
                partOfSpeech = primary?.partOfSpeech,
                definitions = definitions,
                synonyms = synonyms,
            )
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

    @Serializable
    private data class DictionaryEntry(
        val word: String? = null,
        val phonetic: String? = null,
        val phonetics: List<Phonetic> = emptyList(),
        val meanings: List<Meaning> = emptyList(),
    )

    @Serializable
    private data class Phonetic(val text: String? = null)

    @Serializable
    private data class Meaning(
        @SerialName("partOfSpeech") val partOfSpeech: String? = null,
        val definitions: List<Definition> = emptyList(),
        val synonyms: List<String> = emptyList(),
    )

    @Serializable
    private data class Definition(val definition: String? = null)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.dictionaryapi.dev"

        /** BE-21: timeout 3 giây/từ — nguồn phụ, chậm thì bỏ (E21) không chặn kết quả ML Kit. */
        const val TIMEOUT_SECONDS = 3L

        fun defaultHttpClient(): OkHttpClient = GeminiClient.defaultHttpClient()
            .newBuilder()
            .callTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        private const val MAX_DEFINITIONS = 3
        private const val MAX_SYNONYMS = 5
    }
}