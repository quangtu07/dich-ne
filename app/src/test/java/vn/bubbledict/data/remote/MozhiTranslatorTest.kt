package vn.bubbledict.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * Mozhi (nguồn keyless dự phòng): GET câu ngắn, POST đoạn dài, failover 2 instance, lỗi map đúng.
 * Không gọi mạng thật: MockWebServer.
 */
class MozhiTranslatorTest {

    private lateinit var primary: MockWebServer
    private lateinit var secondary: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        primary = MockWebServer().apply { start() }
        secondary = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        primary.shutdown()
        secondary.shutdown()
    }

    private fun translator() = MozhiTranslator(
        instanceUrls = listOf(primary.url("/").toString().trimEnd('/'), secondary.url("/").toString().trimEnd('/')),
        httpClient = client,
        logger = {},
    )

    private fun request(text: String) = LookupRequest(text = text, source = TextSource.SELECTION)

    @Test
    fun shortSentence_usesGetAndParsesTranslatedText() = runBlocking {
        primary.enqueue(
            MockResponse().setBody("""{"translated-text":"Thư viện cũ nằm ở góc phố."}"""),
        )
        val outcome = translator().lookup(request("The old library sits on the corner."))
        assertTrue(outcome is TranslationOutcome.Translation)
        outcome as TranslationOutcome.Translation
        assertEquals("Thư viện cũ nằm ở góc phố.", outcome.text)
        assertEquals("mozhi", outcome.provider)

        val recorded = primary.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("engine=google"))
        assertTrue(recorded.path!!.contains("from=en"))
        assertTrue(recorded.path!!.contains("to=vi"))
    }

    @Test
    fun longParagraph_usesPostForm() = runBlocking {
        primary.enqueue(MockResponse().setBody("""{"translated-text":"Bản dịch dài."}"""))
        val longText = ("The city was quiet. Rain fell all night. " + "Nobody spoke at all. ".repeat(20)).trim()
        val outcome = translator().lookup(request(longText))
        assertTrue(outcome is TranslationOutcome.Translation)

        val recorded = primary.takeRequest()
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("engine=google"))
        assertTrue(body.contains("text="))
    }

    @Test
    fun primaryDown_failoverToSecondary() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500))
        secondary.enqueue(MockResponse().setBody("""{"translated-text":"Dự phòng."}"""))

        val outcome = translator().lookup(request("Fallback please."))
        assertTrue(outcome is TranslationOutcome.Translation)
        assertEquals("Dự phòng.", (outcome as TranslationOutcome.Translation).text)
        assertEquals(1, secondary.requestCount)
    }

    @Test
    fun bothInstancesFail_mapsToUnavailable() = runBlocking {
        // Spec 1.16 / BUG-16: Mozhi không có quota riêng — 429/5xx đều là instance hỏng tạm thời.
        primary.enqueue(MockResponse().setResponseCode(429))
        secondary.enqueue(MockResponse().setResponseCode(503))

        val outcome = translator().lookup(request("Both down."))
        assertTrue(outcome is TranslationOutcome.Failure)
        val failure = outcome as TranslationOutcome.Failure
        assertEquals(FailureReason.UNAVAILABLE, failure.reason)
    }

    @Test
    fun bothInstancesReturn500_mapsToUnavailableNotQuota() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500))
        secondary.enqueue(MockResponse().setResponseCode(500))

        val failure = translator().lookup(request("Both 500.")) as TranslationOutcome.Failure
        assertEquals(FailureReason.UNAVAILABLE, failure.reason)
        assertTrue("5xx không được dán nhãn hết lượt", failure.reason != FailureReason.QUOTA)
    }

    @Test
    fun failedInstance_isSkippedForFiveMinutes() = runBlocking {
        var clock = 1_000_000L
        val translator = MozhiTranslator(
            instanceUrls = listOf(
                primary.url("/").toString().trimEnd('/'),
                secondary.url("/").toString().trimEnd('/'),
            ),
            httpClient = client,
            logger = {},
            now = { clock },
        )

        // Lần 1: primary 500 -> failover sang secondary thành công.
        primary.enqueue(MockResponse().setResponseCode(500))
        secondary.enqueue(MockResponse().setBody("""{"translated-text":"Lần một."}"""))
        assertTrue(translator.lookup(request("First call.")) is TranslationOutcome.Translation)
        assertEquals(1, primary.requestCount)
        assertEquals(1, secondary.requestCount)

        // Lần 2 trong 5 phút: primary bị cache là hỏng -> chỉ gọi secondary.
        clock += 60_000L
        secondary.enqueue(MockResponse().setBody("""{"translated-text":"Lần hai."}"""))
        val second = translator.lookup(request("Second call."))
        assertTrue(second is TranslationOutcome.Translation)
        assertEquals("Lần hai.", (second as TranslationOutcome.Translation).text)
        assertEquals("primary không được gọi lại trong 5 phút", 1, primary.requestCount)
        assertEquals(2, secondary.requestCount)

        // Sau 5 phút: cho primary thử lại (đã hồi).
        clock += MozhiTranslator.INSTANCE_DOWN_CACHE_MS
        primary.enqueue(MockResponse().setBody("""{"translated-text":"Primary hồi."}"""))
        val third = translator.lookup(request("Third call."))
        assertTrue(third is TranslationOutcome.Translation)
        assertEquals(2, primary.requestCount)
    }

    @Test
    fun word_isNotApplicable_noRequest() = runBlocking {
        val outcome = translator().lookup(request("serendipity")) // classifier tự nhận WORD
        assertTrue(outcome is TranslationOutcome.Failure)
        assertEquals(FailureReason.NOT_APPLICABLE, (outcome as TranslationOutcome.Failure).reason)
        assertEquals(0, primary.requestCount + secondary.requestCount)
    }

    @Test
    fun emptyOrUnparsableBody_isParseFailure() = runBlocking {
        primary.enqueue(MockResponse().setBody("""{"ok":true}"""))
        secondary.enqueue(MockResponse().setBody("""{"translated-text":""}"""))
        val outcome = translator().lookup(request("Nothing useful."))
        assertTrue(outcome is TranslationOutcome.Failure)
        assertTrue(
            (outcome as TranslationOutcome.Failure).reason == FailureReason.PARSE ||
                outcome.reason == FailureReason.EMPTY,
        )
    }

    @Test
    fun splitForQuery_keepsShortTextWhole_andSplitsLongBySentence() {
        val translator = MozhiTranslator(instanceUrls = listOf("https://example.invalid"))
        assertEquals(listOf("Short one."), translator.splitForQuery("Short one."))

        val long = (1..120).joinToString(" ") { "Sentence number $it is here." } // > 1800 ký tự
        val chunks = translator.splitForQuery(long)
        assertTrue("phải tách thành nhiều chunk", chunks.size > 1)
        assertTrue(chunks.all { it.length <= MozhiTranslator.MAX_CHUNK_CHARS })
        assertEquals(long.replace(Regex("\\s+"), " "), chunks.joinToString(" ").replace(Regex("\\s+"), " "))
    }

    @Test
    fun chineseSource_mapsToZhCn() = runBlocking {
        primary.enqueue(MockResponse().setBody("""{"translated-text":"Hôm nay thời tiết rất đẹp."}"""))
        val chainRequest = LookupRequest(
            "今天天气很好，我们去公园散步。",
            TextSource.SELECTION,
            sourceLang = "zh",
            targetLang = "vi",
        )

        val outcome = translator().lookup(chainRequest)

        assertTrue(outcome is TranslationOutcome.Translation)
        assertTrue(primary.takeRequest().path!!.contains("from=zh-CN"))
    }

    @Test
    fun providerLang_mapsOnlyChinese() {
        val translator = translator()
        assertEquals("zh-CN", translator.providerLang("zh"))
        assertEquals("ja", translator.providerLang("ja"))
        assertEquals("ko", translator.providerLang("KO"))
        assertEquals("vi", translator.providerLang("vi"))
    }
}