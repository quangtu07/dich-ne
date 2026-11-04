package vn.bubbledict.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.bubbledict.data.remote.ExamplePayload
import vn.bubbledict.data.remote.FailureReason
import vn.bubbledict.data.remote.TranslationOutcome
import vn.bubbledict.data.remote.WordPayload
import vn.bubbledict.domain.DictionaryEnricher
import vn.bubbledict.domain.DictionaryInfo
import vn.bubbledict.domain.model.CachedPayload
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.LookupResult
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.Sense
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * BE-10 DoD: fake clients, không mạng/Room/ML Kit.
 * Phủ cache hit, online OK, E4/E5, thang offline O1 (biên 200 ký tự/3 câu), E3a/E3b, E1, E2, ghi lịch sử.
 */
class LookupPipelineTest {

    private val now = 1_700_000_000_000L

    /** 4 câu > ngưỡng O1 (3 câu) -> nhánh "đoạn dài". */
    private val LONG_PARAGRAPH =
        "The city was quiet. Rain fell all night. By morning the streets were empty. Nobody spoke."

    private class FakeOnline(var outcome: TranslationOutcome) : OnlineTranslator {
        var calls = 0
        var lastRequest: LookupRequest? = null
        override suspend fun lookup(request: LookupRequest): TranslationOutcome {
            calls++
            lastRequest = request
            return outcome
        }
    }

    private class FakeOffline(
        var ready: Boolean = true,
        var translation: String? = "Thành phố yên tĩnh.",
    ) : OfflineTranslator {
        var calls = 0
        override suspend fun isModelReady(): Boolean = ready
        override suspend fun translate(text: String): String? {
            calls++
            return translation
        }
    }

    private class FakeCache : LookupCache {
        var payload: CachedPayload? = null
        var writes = 0
        var lastReadKind: TextKind? = null
        var lastWritten: CachedPayload? = null
        override suspend fun read(normText: String, targetLang: String, kind: TextKind): CachedPayload? {
            lastReadKind = kind
            return payload
        }
        override suspend fun write(
            normText: String,
            targetLang: String,
            kind: TextKind,
            payload: CachedPayload,
            engine: Engine,
        ) {
            writes++
            lastWritten = payload
            this.payload = payload // mô phỏng cache thật: lượt sau đọc lại được
        }
    }

    private class FakeHistory : HistorySink {
        val recorded = mutableListOf<Pair<LookupRequest, LookupResult>>()
        val rawTexts = mutableListOf<String>()
        val providerUpdates = mutableListOf<String>()
        override suspend fun record(rawText: String, request: LookupRequest, result: LookupResult) {
            recorded += request to result
            rawTexts += rawText
        }

        override suspend fun updateProvider(normText: String, createdAt: Long, provider: String) {
            providerUpdates += provider
        }
    }

    private class FakeDictionary(
        private val info: DictionaryInfo? = DictionaryInfo(
            word = "ubiquitous",
            ipa = "/juːˈbɪk.wə.təs/",
            partOfSpeech = "adjective",
            definitions = listOf("Being everywhere at once: omnipresent."),
            synonyms = listOf("omnipresent"),
        ),
    ) : DictionaryEnricher {
        var calls = 0
        override suspend fun enrich(word: String): DictionaryInfo? {
            calls++
            return info
        }
    }

    private fun pipeline(
        online: FakeOnline,
        offline: FakeOffline = FakeOffline(),
        cache: FakeCache = FakeCache(),
        history: FakeHistory = FakeHistory(),
        dictionary: FakeDictionary? = null,
    ) = Triple(
        LookupPipeline(
            online = online,
            offline = offline,
            cache = cache,
            history = history,
            dictionary = dictionary,
            now = { now },
        ),
        Triple(offline, cache, history),
        online,
    )

    @Test
    fun wordNoKey_firstRenderHasNoEnrichment() = runBlocking {
        // AC29: hiện nghĩa ML Kit ngay, KHÔNG chờ dictionary (M1 ≤ 3s).
        val online = FakeOnline(failure(FailureReason.NO_KEY))
        val offline = FakeOffline(ready = true, translation = "có mặt khắp nơi")
        val dictionary = FakeDictionary()
        val cache = FakeCache()
        val (pipe, _, _) = pipeline(online, offline = offline, cache = cache, dictionary = dictionary)

        val result = pipe.lookup("ubiquitous")

        assertEquals(TextKind.WORD, result.kind)
        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.MLKIT, result.engine)
        assertEquals("có mặt khắp nơi", result.translation)
        assertNull("lượt đầu chưa có IPA (enrich sau)", result.ipa)
        assertEquals(0, dictionary.calls)
        assertEquals("mlkit", result.provider)
        assertEquals(1, cache.writes)
    }

    @Test
    fun enrichWord_afterRender_addsIpaAndDefinitions() = runBlocking {
        val online = FakeOnline(failure(FailureReason.NO_KEY))
        val offline = FakeOffline(ready = true, translation = "có mặt khắp nơi")
        val dictionary = FakeDictionary()
        val cache = FakeCache()
        val history = FakeHistory()
        val (pipe, _, _) = pipeline(
            online = online,
            offline = offline,
            cache = cache,
            history = history,
            dictionary = dictionary,
        )
        val first = pipe.lookup("ubiquitous")

        val enriched = pipe.enrichWord(first)

        assertNotNull(enriched)
        enriched!!
        assertEquals("/juːˈbɪk.wə.təs/", enriched.ipa)
        assertEquals(listOf("Being everywhere at once: omnipresent."), enriched.englishDefinitions)
        assertEquals("mlkit+dictionaryapi", enriched.provider)
        assertEquals(TextKind.WORD, enriched.kind)
        assertEquals(2, cache.writes) // lượt render + lượt enrich cập nhật cache
        assertEquals("/juːˈbɪk.wə.təs/", cache.lastWritten?.ipa)
        assertEquals(listOf("mlkit+dictionaryapi"), history.providerUpdates) // BUG-08
    }

    @Test
    fun enrichWord_returnsNull_whenDictionaryFails() = runBlocking {
        val dictionary = FakeDictionary(info = null)
        val (pipe, _, _) = pipeline(
            FakeOnline(failure(FailureReason.NO_KEY)),
            offline = FakeOffline(ready = true, translation = "có mặt khắp nơi"),
            dictionary = dictionary,
        )
        val first = pipe.lookup("ubiquitous")

        assertNull("E21: enrich fail thì không cập nhật gì", pipe.enrichWord(first))
        assertNull(first.ipa)
    }

    @Test
    fun mlkitEnglishOutput_markedE22_noFakeMeaning() = runBlocking {
        // E22: ML Kit EN→VI có lúc trả lại chính từ tiếng Anh ("lucid" -> "Lucid").
        val online = FakeOnline(failure(FailureReason.NO_KEY))
        val offline = FakeOffline(ready = true, translation = "Lucid")
        val (pipe, _, _) = pipeline(online, offline = offline)

        val result = pipe.lookup("lucid")

        assertEquals(ResultState.OK, result.state)
        assertNull("không được hiện chuỗi tiếng Anh làm nghĩa", result.translation)
        assertEquals(LookupErrorCodes.E22, result.errorCode)
    }

    @Test
    fun sentenceNoKey_doesNotCallDictionary() = runBlocking {
        val dictionary = FakeDictionary()
        val (pipe, _, _) = pipeline(
            FakeOnline(failure(FailureReason.NO_KEY)),
            offline = FakeOffline(ready = true, translation = "Thành phố yên tĩnh."),
            dictionary = dictionary,
        )

        val result = pipe.lookup("The city was quiet.")

        assertEquals(Engine.MLKIT, result.engine)
        assertEquals(0, dictionary.calls)
        assertTrue(result.englishDefinitions.isEmpty())
    }

    private fun failure(reason: FailureReason, status: Int? = null) =
        TranslationOutcome.Failure("E4", reason, httpStatus = status)

    private fun wordOutcome() = TranslationOutcome.Word(
        WordPayload(
            meaning = "có mặt khắp nơi",
            partOfSpeech = "adj",
            ipa = "/juːˈbɪkwɪtəs/",
            examples = listOf(ExamplePayload("Cars are ubiquitous.", "Xe hơi có mặt khắp nơi.")),
            synonyms = listOf("omnipresent"),
        ),
    )

    // --- cache ---

    @Test
    fun cacheHit_returnsCachedWithoutCallingOnline() = runBlocking {
        val online = FakeOnline(wordOutcome())
        val cache = FakeCache().apply { payload = CachedPayload(translation = "bản đã lưu") }
        val history = FakeHistory()
        val (pipe, deps, _) = pipeline(online, cache = cache, history = history)

        val result = pipe.lookup("The city was quiet.")

        assertEquals(ResultState.CACHED, result.state)
        assertEquals(Engine.CACHE, result.engine)
        assertEquals("bản đã lưu", result.translation)
        assertEquals(0, online.calls)
        assertEquals(1, history.recorded.size)
    }

    // --- online ---

    @Test
    fun wordOnline_returnsOkGeminiAndCaches() = runBlocking {
        val online = FakeOnline(wordOutcome())
        val cache = FakeCache()
        val history = FakeHistory()
        val (pipe, _, _) = pipeline(online, cache = cache, history = history)

        val result = pipe.lookup("ubiquitous")

        assertEquals(TextKind.WORD, result.kind)
        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.KEY, result.engine)
        assertEquals(listOf(Sense("có mặt khắp nơi", "adj")), result.senses)
        assertEquals("/juːˈbɪkwɪtəs/", result.ipa)
        assertEquals("Xe hơi có mặt khắp nơi.", result.examples.single().translation)
        assertEquals(1, cache.writes)
        assertEquals(ResultState.OK, history.recorded.single().second.state)
    }

    @Test
    fun sentenceOnline_returnsTranslation() = runBlocking {
        val online = FakeOnline(TranslationOutcome.Translation("Thành phố yên tĩnh."))
        val (pipe, _, _) = pipeline(online)

        val result = pipe.lookup("The city was quiet.")

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.KEY, result.engine)
        assertEquals("Thành phố yên tĩnh.", result.translation)
    }

    @Test
    fun quotaLongText_mapsToE19AndRecordsHistory() = runBlocking {
        // Spec AC25: hết quota keyless + đoạn dài -> E19 "hết lượt miễn phí", KHÔNG phải "cần mạng".
        val online = FakeOnline(failure(FailureReason.QUOTA, 429))
        val cache = FakeCache()
        val history = FakeHistory()
        val (pipe, _, _) = pipeline(online, cache = cache, history = history)

        val result = pipe.lookup(LONG_PARAGRAPH)

        assertEquals(ResultState.ERROR, result.state)
        assertNull(result.engine)
        assertEquals(LookupErrorCodes.E19, result.errorCode)
        assertEquals(0, cache.writes)
        assertEquals(LookupErrorCodes.E19, history.recorded.single().second.errorCode)
    }

    @Test
    fun quotaShortTextWithModel_fallsBackToMlkit() = runBlocking {
        // Spec AC25: trong ngưỡng offline thì phải ra kết quả ML Kit, không hiện lỗi.
        val online = FakeOnline(failure(FailureReason.QUOTA, 429))
        val offline = FakeOffline(ready = true, translation = "Thành phố yên tĩnh.")
        val (pipe, _, _) = pipeline(online, offline = offline)

        val result = pipe.lookup("The city was quiet.")

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.MLKIT, result.engine)
    }

    @Test
    fun invalidKeyLongText_mapsToE20() = runBlocking {
        val (pipe, _, _) = pipeline(FakeOnline(failure(FailureReason.HTTP, 401)))

        assertEquals(LookupErrorCodes.E20, pipe.lookup(LONG_PARAGRAPH).errorCode)
    }

    @Test
    fun keylessTranslation_longText_isOk() = runBlocking {
        // AC24: không key + có mạng + đoạn dài -> vẫn có bản dịch (nguồn keyless trả về).
        val online = FakeOnline(TranslationOutcome.Translation("Thành phố yên tĩnh. Mưa rơi suốt đêm. Đến sáng đường phố vắng tanh. Không ai nói gì."))
        val (pipe, _, _) = pipeline(online)

        val result = pipe.lookup(LONG_PARAGRAPH)

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.KEY, result.engine)
    }

    @Test
    fun onlineDisabled_neverCallsNetwork() = runBlocking {
        // AC26: tắt công tắc online -> chỉ cache/ML Kit.
        val online = FakeOnline(TranslationOutcome.Translation("không được gọi"))
        val offline = FakeOffline(ready = true, translation = "Xin chào")
        val pipe = LookupPipeline(
            online = online,
            offline = offline,
            cache = FakeCache(),
            history = FakeHistory(),
            onlineAllowed = { false },
            now = { now },
        )

        val result = pipe.lookup("hello")

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.MLKIT, result.engine)
        assertEquals(0, online.calls)
    }

    @Test
    fun timeoutLongText_mapsToE4() = runBlocking {
        val (pipe, _, _) = pipeline(FakeOnline(failure(FailureReason.TIMEOUT)))

        assertEquals(LookupErrorCodes.E4, pipe.lookup(LONG_PARAGRAPH).errorCode)
    }

    @Test
    fun emptyAnswer_mapsToE5() = runBlocking {
        val (pipe, _, _) = pipeline(FakeOnline(failure(FailureReason.EMPTY)))

        assertEquals(LookupErrorCodes.E5, pipe.lookup(LONG_PARAGRAPH).errorCode)
    }

    @Test
    fun englishAnswer_mapsToE5() = runBlocking {
        val (pipe, _, _) = pipeline(FakeOnline(failure(FailureReason.NOT_VIETNAMESE)))

        assertEquals(LookupErrorCodes.E5, pipe.lookup(LONG_PARAGRAPH).errorCode)
    }

    // --- offline (O1) ---

    @Test
    fun offlineWithModelAndShortText_returnsMlkit() = runBlocking {
        val online = FakeOnline(failure(FailureReason.IO))
        val offline = FakeOffline(ready = true, translation = "Xin chào")
        val cache = FakeCache()
        val (pipe, _, _) = pipeline(online, offline = offline, cache = cache)

        val result = pipe.lookup("hello")

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.MLKIT, result.engine)
        assertEquals("Xin chào", result.translation)
        assertEquals(1, cache.writes) // F8: kết quả offline thành công cũng ghi cache
        // O2: offline không có phiên âm/ví dụ
        assertNull(result.ipa)
        assertTrue(result.examples.isEmpty())
    }

    @Test
    fun offlineResult_isCached_secondLookupIsCacheHit() = runBlocking {
        // AC17: lượt offline đầu -> OK/MLKIT; lượt 2 (vẫn offline) -> CACHED/CACHE, không dịch lại.
        val online = FakeOnline(failure(FailureReason.IO))
        val offline = FakeOffline(ready = true, translation = "sáng suốt")
        val cache = FakeCache()
        val (pipe, _, _) = pipeline(online, offline = offline, cache = cache)

        val first = pipe.lookup("perspicacious")
        val second = pipe.lookup("perspicacious")

        assertEquals(ResultState.OK, first.state)
        assertEquals(Engine.MLKIT, first.engine)
        assertEquals(1, cache.writes)

        assertEquals(ResultState.CACHED, second.state)
        assertEquals(Engine.CACHE, second.engine)
        assertEquals("sáng suốt", second.translation)
        assertEquals(1, offline.calls) // không gọi ML Kit lần 2
    }

    @Test
    fun offlineWithoutModel_returnsE3a() = runBlocking {
        val online = FakeOnline(failure(FailureReason.IO))
        val offline = FakeOffline(ready = false)
        val (pipe, deps, _) = pipeline(online, offline = offline)
        val (_, _, history) = deps

        val result = pipe.lookup("hello")

        assertEquals(LookupErrorCodes.E3A, result.errorCode)
        assertEquals(0, offline.calls)
        assertEquals(LookupErrorCodes.E3A, history.recorded.single().second.errorCode)
    }

    @Test
    fun offlineLongParagraph_returnsE3b() = runBlocking {
        val online = FakeOnline(failure(FailureReason.IO))
        val offline = FakeOffline(ready = true)
        val longText = "The city was quiet. Rain fell all night. By morning the streets were empty. Nobody spoke."
        val (pipe, _, _) = pipeline(online, offline = offline)

        val result = pipe.lookup(longText)

        assertEquals(LookupErrorCodes.E3B, result.errorCode)
        assertEquals(0, offline.calls)
    }

    @Test
    fun offlineTooManyChars_returnsE3b() = runBlocking {
        val online = FakeOnline(failure(FailureReason.IO))
        val text = "a".repeat(201)
        val (pipe, _, _) = pipeline(online)

        assertEquals(LookupErrorCodes.E3B, pipe.lookup(text).errorCode)
    }

    @Test
    fun offlineBoundary200CharsAnd3Sentences_usesMlkit() = runBlocking {
        val online = FakeOnline(failure(FailureReason.IO))
        val offline = FakeOffline(ready = true)
        // 3 câu, đúng 200 ký tự
        val text = ("One. Two. " + "a".repeat(200 - 10)).also { assertEquals(200, it.length) }
        val (pipe, _, _) = pipeline(online, offline = offline)

        val result = pipe.lookup(text)

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.MLKIT, result.engine)
        assertEquals(1, offline.calls)
    }

    @Test
    fun offlineBoundary201Chars_isE3b() = runBlocking {
        val (pipe, _, _) = pipeline(FakeOnline(failure(FailureReason.IO)))
        assertEquals(LookupErrorCodes.E3B, pipe.lookup("a".repeat(201)).errorCode)
    }

    @Test
    fun offlineTranslateFails_isE3a() = runBlocking {
        val offline = FakeOffline(ready = true, translation = null)
        val (pipe, _, _) = pipeline(FakeOnline(failure(FailureReason.IO)), offline = offline)

        assertEquals(LookupErrorCodes.E3A, pipe.lookup("hello").errorCode)
    }

    @Test
    fun noKey_behavesLikeOffline() = runBlocking {
        val online = FakeOnline(failure(FailureReason.NO_KEY))
        val offline = FakeOffline(ready = true, translation = "Xin chào")
        val (pipe, _, _) = pipeline(online, offline = offline)

        val result = pipe.lookup("hello")

        assertEquals(ResultState.OK, result.state)
        assertEquals(Engine.MLKIT, result.engine)
    }

    // --- E1/E2 ---

    @Test
    fun blankText_isE1_noOnlineCall() = runBlocking {
        val online = FakeOnline(wordOutcome())
        val history = FakeHistory()
        val (pipe, _, _) = pipeline(online, history = history)

        val result = pipe.lookup("   \n ")

        assertEquals(LookupErrorCodes.E1, result.errorCode)
        assertEquals(0, online.calls)
        assertEquals(LookupErrorCodes.E1, history.recorded.single().second.errorCode)
    }

    @Test
    fun longText_isTruncatedTo2000() = runBlocking {
        val online = FakeOnline(TranslationOutcome.Translation("Bản dịch."))
        val (pipe, _, _) = pipeline(online)

        val result = pipe.lookup("word ".repeat(500))

        assertEquals(LookupPipeline.MAX_TEXT_CHARS, result.truncatedTo)
        assertEquals(LookupPipeline.MAX_TEXT_CHARS, online.lastRequest!!.text.length)
    }

    @Test
    fun historyKeepsRawTextAndSource() = runBlocking {
        val history = FakeHistory()
        val (pipe, _, _) = pipeline(FakeOnline(TranslationOutcome.Translation("Bản dịch.")), history = history)

        pipe.lookup("  Hello   World  ", source = TextSource.OCR, sourceApp = null)

        assertEquals("  Hello   World  ", history.rawTexts.single())
        assertEquals(TextSource.OCR, history.recorded.single().first.source)
    }
}