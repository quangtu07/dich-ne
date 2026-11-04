package vn.bubbledict.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Từ điển keyless (dictionaryapi.dev) dùng bổ sung IPA + định nghĩa tiếng Anh cho WORD
 * khi user không có key. Lỗi/404 -> null, không chặn kết quả ML Kit.
 */
class FreeDictionaryApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = FreeDictionaryApi(baseUrl = server.url("/").toString().trimEnd('/'))

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun parsesIpaDefinitionsAndSynonyms() = runBlocking {
        enqueue(
            """[{"word":"ubiquitous","phonetic":"/juːˈbɪk.wə.təs/","phonetics":[{"text":"/juːˈbɪk.wə.təs/"}],
               "meanings":[{"partOfSpeech":"adjective",
                            "definitions":[{"definition":"Being everywhere at once: omnipresent."}],
                            "synonyms":["omnipresent","pervasive"]}]}]""",
        )

        val info = api().enrich("ubiquitous")

        assertEquals("/juːˈbɪk.wə.təs/", info?.ipa)
        assertEquals("adjective", info?.partOfSpeech)
        assertEquals(listOf("Being everywhere at once: omnipresent."), info?.definitions)
        assertEquals(listOf("omnipresent", "pervasive"), info?.synonyms)
        assertTrue(server.takeRequest().path!!.contains("/api/v2/entries/en/ubiquitous"))
    }

    @Test
    fun ipaFromPhoneticsArray_whenTopLevelPhoneticMissing() = runBlocking {
        enqueue(
            """[{"word":"frail","phonetics":[{"text":""},{"text":"/freɪl/"}],
               "meanings":[{"partOfSpeech":"adjective","definitions":[{"definition":"Weak and delicate."}]}]}]""",
        )

        val info = api().enrich("frail")

        assertEquals("/freɪl/", info?.ipa)
        assertEquals(listOf("Weak and delicate."), info?.definitions)
    }

    @Test
    fun notFound_returnsNull() = runBlocking {
        enqueue("""{"title":"No Definitions Found"}""", code = 404)

        assertNull(api().enrich("zzzznotaword"))
    }

    @Test
    fun malformedJson_returnsNull() = runBlocking {
        enqueue("not json")

        assertNull(api().enrich("ubiquitous"))
    }

    @Test
    fun emptyEntry_returnsNull() = runBlocking {
        enqueue("[]")

        assertNull(api().enrich("ubiquitous"))
    }

    @Test
    fun multiWordText_doesNotCallNetwork() = runBlocking {
        assertNull(api().enrich("the city was quiet"))
        assertEquals(0, server.requestCount)
    }
}