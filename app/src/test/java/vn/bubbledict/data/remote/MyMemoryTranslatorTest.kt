package vn.bubbledict.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextSource

/**
 * MyMemory (nguồn keyless, spec 1.5 F11):
 * - câu/đoạn -> bản dịch tiếng Việt;
 * - WORD -> NOT_APPLICABLE (không gửi request);
 * - hết quota (`quotaFinished`, 429, 5xx) -> QUOTA để pipeline rơi ML Kit (E19);
 * - trả nguyên văn tiếng Anh -> NOT_VIETNAMESE;
 * - request dài bị chia theo byte UTF-8 (≤450 bytes).
 */
class MyMemoryTranslatorTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun translator() = MyMemoryTranslator(
        baseUrl = server.url("/").toString().trimEnd('/'),
        logger = { logs += it },
    )

    private fun body(translated: String, quotaFinished: Boolean = false, status: Int = 200) =
        """{"responseData":{"translatedText":"$translated"},"quotaFinished":$quotaFinished,"responseStatus":$status}"""

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private val sentence = LookupRequest("The city was quiet.", TextSource.SELECTION)
    private val word = LookupRequest("perspicacious", TextSource.SELECTION)

    @Test
    fun sentence_returnsVietnameseTranslation() = runBlocking {
        enqueue(body("Thành phố yên tĩnh."))

        val outcome = translator().lookup(sentence)

        assertTrue("phải là Translation, thực tế: $outcome", outcome is TranslationOutcome.Translation)
        assertEquals("Thành phố yên tĩnh.", (outcome as TranslationOutcome.Translation).text)
        assertEquals("mymemory", outcome.provider)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("langpair=en%7Cvi"))
        assertTrue(request.path!!.contains("q=The"))
    }

    @Test
    fun word_isNotApplicable_noRequestSent() = runBlocking {
        val outcome = translator().lookup(word)

        val failure = outcome as TranslationOutcome.Failure
        assertEquals(FailureReason.NOT_APPLICABLE, failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun wordWithQuotaExhausted_isStillNotApplicable_notQuota() = runBlocking {
        // BUG-17: tra TỪ khi MyMemory hết quota phải trả NOT_APPLICABLE (để rơi ML Kit),
        // KHÔNG được trả QUOTA -> tránh UI hiện E19 "hết lượt dịch miễn phí" cho tra từ.
        val quotaBlocked = MyMemoryTranslator(
            baseUrl = server.url("/").toString().trimEnd('/'),
            isQuotaBlockedToday = { true },
            logger = { logs += it },
        )

        val failure = quotaBlocked.lookup(word) as TranslationOutcome.Failure

        assertEquals(FailureReason.NOT_APPLICABLE, failure.reason)
        assertEquals("WORD không được gọi mạng", 0, server.requestCount)
    }

    @Test
    fun quotaFinished_mapsToQuota() = runBlocking {
        enqueue(body("Thành phố yên tĩnh.", quotaFinished = true))

        val failure = translator().lookup(sentence) as TranslationOutcome.Failure

        assertEquals(FailureReason.QUOTA, failure.reason)
        assertEquals(429, failure.httpStatus)
    }

    @Test
    fun http429_mapsToQuota() = runBlocking {
        enqueue("{}", code = 429)

        assertEquals(FailureReason.QUOTA, (translator().lookup(sentence) as TranslationOutcome.Failure).reason)
    }

    @Test
    fun http500_mapsToUnavailable() = runBlocking {
        enqueue("{}", code = 500)

        // BUG-16 / spec 1.16: 5xx là nguồn hỏng tạm thời, KHÔNG phải hết lượt.
        assertEquals(
            FailureReason.UNAVAILABLE,
            (translator().lookup(sentence) as TranslationOutcome.Failure).reason,
        )
    }

    @Test
    fun quotaInBodyStatus429_mapsToQuota() = runBlocking {
        // MyMemory có thể trả HTTP 200 + responseStatus 429 khi hết lượt.
        enqueue(body("Thành phố yên tĩnh.", status = 429))

        assertEquals(
            FailureReason.QUOTA,
            (translator().lookup(sentence) as TranslationOutcome.Failure).reason,
        )
    }

    @Test
    fun queryTooLong_mapsToUnavailableNotQuota() = runBlocking {
        enqueue(body("QUERY LENGTH LIMIT EXCEEDED. MAX ALLOWED QUERY : 500 CHARS", status = 403))

        assertEquals(
            FailureReason.UNAVAILABLE,
            (translator().lookup(sentence) as TranslationOutcome.Failure).reason,
        )
    }

    @Test
    fun unchangedEnglish_mapsToNotVietnamese() = runBlocking {
        enqueue(body("The city was quiet."))

        val failure = translator().lookup(sentence) as TranslationOutcome.Failure

        assertEquals(FailureReason.NOT_VIETNAMESE, failure.reason)
    }

    @Test
    fun blankTranslation_mapsToEmpty() = runBlocking {
        enqueue(body("   "))

        assertEquals(FailureReason.EMPTY, (translator().lookup(sentence) as TranslationOutcome.Failure).reason)
    }

    @Test
    fun longText_isChunkedUnderByteLimit() = runBlocking {
        val longText = "The city was quiet. Rain fell all night. By morning the streets were empty. ".repeat(8)
        assertTrue("text phải > 450 bytes để test chia nhỏ", longText.toByteArray().size > 450)
        repeat(6) { enqueue(body("Bản dịch.")) }

        val outcome = translator().lookup(LookupRequest(longText, TextSource.SELECTION))

        assertTrue("phải chia thành nhiều request", server.requestCount >= 2)
        // mỗi query không vượt giới hạn byte của MyMemory (500)
        repeat(server.requestCount) {
            val path = server.takeRequest().path!!
            val q = java.net.URLDecoder.decode(path.substringAfter("q=").substringBefore("&"), "UTF-8")
            assertTrue("query ${q.toByteArray().size} bytes vượt 450", q.toByteArray().size <= MyMemoryTranslator.MAX_QUERY_BYTES)
        }
        assertTrue(outcome is TranslationOutcome.Translation)
    }

    @Test
    fun requestIsAnonymous_neverSendsDeParameter() = runBlocking {
        // Spec 1.17 §5.9: không gửi email liên hệ (`de=`) — MyMemory gọi ẩn danh.
        enqueue(body("Thành phố yên tĩnh."))

        translator().lookup(sentence)

        val path = server.takeRequest().path!!
        assertTrue("không được gửi de=: $path", !path.contains("de="))
    }

    @Test
    fun logsNeverContainTextOrUrl() = runBlocking {
        enqueue("{}", code = 500)
        translator().lookup(sentence)

        logs.forEach { line ->
            assertTrue("log không được chứa text user: $line", !line.contains("city"))
            assertTrue("log không được chứa URL: $line", !line.contains("mymemory.translated"))
        }
    }

    @Test
    fun quotaBlockedToday_skipsRequest() = runBlocking {
        var marked = false
        val translator = MyMemoryTranslator(
            baseUrl = server.url("/").toString().trimEnd('/'),
            isQuotaBlockedToday = { true },
            markQuotaExhausted = { marked = true },
        )

        val failure = translator.lookup(sentence) as TranslationOutcome.Failure

        assertEquals(FailureReason.QUOTA, failure.reason)
        assertEquals("hết quota hôm nay thì không gọi lại", 0, server.requestCount)
        assertTrue("đã chặn từ đầu thì không đánh dấu lại", !marked)
    }

    @Test
    fun quotaFinished_marksDayExhausted() = runBlocking {
        var marked = false
        val translator = MyMemoryTranslator(
            baseUrl = server.url("/").toString().trimEnd('/'),
            markQuotaExhausted = { marked = true },
        )
        enqueue(body("Thành phố yên tĩnh.", quotaFinished = true))

        translator.lookup(sentence)

        assertTrue("hết quota phải được đánh dấu để bỏ qua MyMemory tới hết ngày", marked)
    }
}
