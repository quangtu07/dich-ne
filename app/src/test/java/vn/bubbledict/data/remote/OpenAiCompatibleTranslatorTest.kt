package vn.bubbledict.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextSource

/**
 * Provider OpenAI-compatible (Groq/OpenRouter/Ollama): dùng cho WORD + câu/đoạn chất lượng cao
 * khi user nhập key. Không key -> NO_KEY để chain bỏ qua.
 */
class OpenAiCompatibleTranslatorTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()
    private val apiKey = "gsk_FAKE_KEY_MUST_NOT_BE_LOGGED"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun translator(key: String? = apiKey) = OpenAiCompatibleTranslator(
        configProvider = {
            OpenAiConfig(
                apiKey = key,
                baseUrl = server.url("/openai/v1").toString().trimEnd('/'),
                model = "llama-3.3-70b-versatile",
            )
        },
        logger = { logs += it },
    )

    private fun chatResponse(content: String): String {
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"choices":[{"message":{"role":"assistant","content":"$escaped"}}]}"""
    }

    private fun enqueue(content: String, code: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(chatResponse(content)),
        )
    }

    private val word = LookupRequest("ubiquitous", TextSource.SELECTION)
    private val sentence = LookupRequest("The city was quiet.", TextSource.SELECTION)

    @Test
    fun word_parsesJsonPayload() = runBlocking {
        enqueue("""{"meaning":"có mặt khắp nơi","partOfSpeech":"adj","ipa":"/juːˈbɪkwɪtəs/" }""")

        val outcome = translator().lookup(word)

        assertTrue("phải là Word, thực tế: $outcome", outcome is TranslationOutcome.Word)
        val payload = (outcome as TranslationOutcome.Word).payload
        assertEquals("có mặt khắp nơi", payload.meaning)
        assertEquals("adj", payload.partOfSpeech)
        assertEquals("/juːˈbɪkwɪtəs/", payload.ipa)

        val request = server.takeRequest()
        assertEquals("Bearer $apiKey", request.getHeader("Authorization"))
        assertTrue(request.path!!.endsWith("/chat/completions"))
        assertFalse("log không chứa key", logs.any { it.contains(apiKey) })
    }

    @Test
    fun word_jsonInCodeFence_isParsed() = runBlocking {
        enqueue("```json\n{\"meaning\":\"có mặt khắp nơi\"}\n```")

        val outcome = translator().lookup(word) as TranslationOutcome.Word

        assertEquals("có mặt khắp nơi", outcome.payload.meaning)
    }

    @Test
    fun sentence_returnsTranslation() = runBlocking {
        enqueue("Thành phố yên tĩnh.")

        val outcome = translator().lookup(sentence)

        assertTrue("phải là Translation, thực tế: $outcome", outcome is TranslationOutcome.Translation)
        assertEquals("Thành phố yên tĩnh.", (outcome as TranslationOutcome.Translation).text)
        assertEquals("openai-compatible", outcome.provider)
    }

    @Test
    fun missingKey_isNoKey_noRequest() = runBlocking {
        val failure = translator(key = null).lookup(word) as TranslationOutcome.Failure

        assertEquals(FailureReason.NO_KEY, failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun http401_mapsToHttpWithStatus() = runBlocking {
        enqueue("{}", code = 401)

        val failure = translator().lookup(word) as TranslationOutcome.Failure

        assertEquals(FailureReason.HTTP, failure.reason)
        assertEquals(401, failure.httpStatus)
    }

    @Test
    fun emptyContent_mapsToEmpty() = runBlocking {
        enqueue("")

        assertEquals(FailureReason.EMPTY, (translator().lookup(sentence) as TranslationOutcome.Failure).reason)
    }

    @Test
    fun brokenWordJson_mapsToParse() = runBlocking {
        enqueue("not json")

        assertEquals(FailureReason.PARSE, (translator().lookup(word) as TranslationOutcome.Failure).reason)
    }

    @Test
    fun englishAnswer_mapsToNotVietnamese() = runBlocking {
        enqueue("Hello world")

        assertEquals(
            FailureReason.NOT_VIETNAMESE,
            (translator().lookup(sentence) as TranslationOutcome.Failure).reason,
        )
    }

    @Test
    fun logsNeverContainKey() = runBlocking {
        enqueue("{}", code = 500)
        translator().lookup(word)

        assertTrue(logs.isNotEmpty())
        logs.forEach { line ->
            assertFalse("log chứa key: $line", line.contains(apiKey))
            assertFalse("log chứa URL: $line", line.contains("chat/completions"))
        }
    }
}