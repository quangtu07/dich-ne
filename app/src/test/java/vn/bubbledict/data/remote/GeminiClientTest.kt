package vn.bubbledict.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextSource
import java.util.concurrent.TimeUnit

/**
 * BE-08: MockWebServer — parse đúng 3 chế độ, map 429/500/timeout/rỗng/không-phải-tiếng-Việt,
 * và log không chứa key.
 */
class GeminiClientTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val fakeKey = "AIzaFAKE_KEY_MUST_NOT_BE_LOGGED"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        httpClient: OkHttpClient = GeminiClient.defaultHttpClient(),
        key: String? = fakeKey,
    ) = GeminiClient(
        apiKeyProvider = { key },
        httpClient = httpClient,
        baseUrl = server.url("/").toString().trimEnd('/'),
        logger = { logs += it },
    )

    /** Bọc text vào envelope `candidates[0].content.parts[0].text` như Gemini trả về. */
    private fun envelope(text: String, finish: String = "STOP", parts: Int = 1): String {
        val partsJson = List(parts) { partText ->
            buildJsonObject { put("text", if (parts == 1) text else "$text-$partText") }
        }
        return buildJsonObject {
            putJsonArray("candidates") {
                add(
                    buildJsonObject {
                        putJsonObject("content") {
                            putJsonArray("parts") { partsJson.forEach { add(it) } }
                        }
                        put("finishReason", finish)
                    },
                )
            }
        }.toString()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun wordRequest() = LookupRequest("ubiquitous", TextSource.SELECTION)
    private fun sentenceRequest() = LookupRequest("The city was quiet.", TextSource.SELECTION)
    private fun paragraphRequest() = LookupRequest(
        "The city was quiet. Rain fell all night. By morning the streets were empty.",
        TextSource.SELECTION,
    )

    @Test
    fun word200_parsesPayload() = runBlocking {
        val payload = WordPayload(
            meaning = "có mặt khắp nơi",
            partOfSpeech = "adj",
            ipa = "/juːˈbɪkwɪtəs/",
            examples = listOf(ExamplePayload("Cars are ubiquitous.", "Xe hơi có mặt khắp nơi.")),
            synonyms = listOf("omnipresent"),
        )
        enqueue(envelope(json.encodeToString(payload)))

        val outcome = client().lookup(wordRequest())

        assertTrue("kết quả phải là Word, thực tế: $outcome", outcome is TranslationOutcome.Word)
        val parsed = (outcome as TranslationOutcome.Word).payload
        assertEquals("có mặt khắp nơi", parsed.meaning)
        assertEquals("adj", parsed.partOfSpeech)
        assertEquals(1, parsed.examples.size)
        assertEquals("Xe hơi có mặt khắp nơi.", parsed.examples[0].translation)
        assertEquals(listOf("omnipresent"), parsed.synonyms)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("/models/gemini-2.5-flash-lite:generateContent"))
        val sentBody = recorded.body.readUtf8()
        assertTrue("request WORD phải kèm responseSchema", sentBody.contains("responseSchema"))
        assertTrue(sentBody.contains("ubiquitous"))
    }

    @Test
    fun sentence200_returnsTranslation() = runBlocking {
        enqueue(envelope("Thành phố yên tĩnh."))

        val outcome = client().lookup(sentenceRequest())

        assertTrue(outcome is TranslationOutcome.Translation)
        assertEquals("Thành phố yên tĩnh.", (outcome as TranslationOutcome.Translation).text)
        val sentBody = server.takeRequest().body.readUtf8()
        assertFalse("câu không dùng responseSchema", sentBody.contains("responseSchema"))
    }

    @Test
    fun paragraph200_keepsAllThreeSentences() = runBlocking {
        enqueue(envelope("Thành phố yên tĩnh. Mưa rơi suốt đêm. Đến sáng đường phố vắng tanh."))

        val outcome = client().lookup(paragraphRequest())

        assertTrue(outcome is TranslationOutcome.Translation)
        val text = (outcome as TranslationOutcome.Translation).text
        assertEquals("phải giữ đủ 3 câu", 3, text.count { it == '.' })
    }

    @Test
    fun http429_mapsToE4() = runBlocking {
        enqueue("""{"error":{"code":429,"message":"quota"}}""", code = 429)

        val outcome = client().lookup(wordRequest())

        val failure = outcome as TranslationOutcome.Failure
        assertEquals(HttpErrors.E4, failure.code)
        assertEquals(FailureReason.HTTP, failure.reason)
        assertEquals(429, failure.httpStatus)
    }

    @Test
    fun http500_mapsToE4() = runBlocking {
        enqueue("""{"error":{"code":500}}""", code = 500)

        val failure = client().lookup(paragraphRequest()) as TranslationOutcome.Failure

        assertEquals(HttpErrors.E4, failure.code)
        assertEquals(500, failure.httpStatus)
    }

    @Test
    fun timeout_mapsToE4() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val fastClient = OkHttpClient.Builder()
            .callTimeout(300, TimeUnit.MILLISECONDS)
            .readTimeout(300, TimeUnit.MILLISECONDS)
            .build()

        val failure = client(httpClient = fastClient).lookup(sentenceRequest()) as TranslationOutcome.Failure

        assertEquals(HttpErrors.E4, failure.code)
        assertEquals(FailureReason.TIMEOUT, failure.reason)
    }

    @Test
    fun emptyCandidates_mapsToE5() = runBlocking {
        enqueue("""{"candidates":[]}""")

        val failure = client().lookup(sentenceRequest()) as TranslationOutcome.Failure

        assertEquals(HttpErrors.E5, failure.code)
        assertEquals(FailureReason.EMPTY, failure.reason)
    }

    @Test
    fun englishAnswer_mapsToE5() = runBlocking {
        enqueue(envelope("Hello world this is English"))

        val failure = client().lookup(sentenceRequest()) as TranslationOutcome.Failure

        assertEquals(HttpErrors.E5, failure.code)
        assertEquals(FailureReason.NOT_VIETNAMESE, failure.reason)
    }

    @Test
    fun wordEnglishMeaning_mapsToE5() = runBlocking {
        enqueue(envelope("""{"meaning":"existing everywhere"}"""))

        val failure = client().lookup(wordRequest()) as TranslationOutcome.Failure

        assertEquals(HttpErrors.E5, failure.code)
        assertEquals(FailureReason.NOT_VIETNAMESE, failure.reason)
    }

    @Test
    fun wordBrokenJson_mapsToE5Parse() = runBlocking {
        enqueue(envelope("not a json object"))

        val failure = client().lookup(wordRequest()) as TranslationOutcome.Failure

        assertEquals(HttpErrors.E5, failure.code)
        assertEquals(FailureReason.PARSE, failure.reason)
    }

    @Test
    fun missingKey_noRequestSent() = runBlocking {
        val failure = client(key = null).lookup(wordRequest()) as TranslationOutcome.Failure

        assertEquals(FailureReason.NO_KEY, failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun multipleParts_areConcatenated() = runBlocking {
        enqueue(envelope("Thành phố yên tĩnh.", parts = 2))

        val outcome = client().lookup(sentenceRequest()) as TranslationOutcome.Translation

        assertEquals("Thành phố yên tĩnh.-0Thành phố yên tĩnh.-1", outcome.text)
    }

    @Test
    fun logsNeverContainKeyNorUrl() = runBlocking {
        enqueue("""{"error":{"code":500}}""", code = 500)
        client().lookup(wordRequest())
        enqueue(envelope("Thành phố yên tĩnh."))
        client().lookup(sentenceRequest())

        assertTrue("phải có log sự kiện", logs.isNotEmpty())
        logs.forEach { line ->
            assertFalse("log chứa key: $line", line.contains(fakeKey))
            assertFalse("log chứa URL: $line", line.contains("generativelanguage"))
            assertFalse("log chứa text user: $line", line.contains("ubiquitous"))
        }
    }

    // --- checkKey (nút "Kiểm tra key" ở Settings) ---

    @Test
    fun checkKey_ok() = runBlocking {
        enqueue(envelope("""{"meaning":"xin chào"}"""))

        assertEquals(KeyCheckResult.OK, client().checkKey())
    }

    @Test
    fun checkKey_missingIsMissing() = runBlocking {
        assertEquals(KeyCheckResult.MISSING, client(key = null).checkKey())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun checkKey_403IsInvalid() = runBlocking {
        enqueue("""{"error":{"code":403}}""", code = 403)

        assertEquals(KeyCheckResult.INVALID, client().checkKey())
    }

    @Test
    fun checkKey_429IsQuota() = runBlocking {
        enqueue("""{"error":{"code":429}}""", code = 429)

        assertEquals(KeyCheckResult.QUOTA, client().checkKey())
    }

    @Test
    fun checkKey_serverDownIsUnreachable() = runBlocking {
        server.shutdown()

        assertEquals(KeyCheckResult.UNREACHABLE, client().checkKey())
    }
}
