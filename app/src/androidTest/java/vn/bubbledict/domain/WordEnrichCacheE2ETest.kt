package vn.bubbledict.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.data.CacheRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.offline.MlkitOfflineTranslator
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.data.remote.FailureReason
import vn.bubbledict.data.remote.FreeDictionaryApi
import vn.bubbledict.data.remote.HttpErrors
import vn.bubbledict.data.remote.TranslationOutcome
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind

/**
 * AC28/CACHE: sau enrich, payload cache phải giữ `englishDefinitions` + `synonyms`
 * để lượt cache-hit (không gọi mạng) vẫn hiện được khối "Nghĩa tiếng Anh"/"Từ gần nghĩa".
 *
 * Dùng mạng thật: ML Kit (model đã tải trên emulator) + dictionaryapi.dev.
 */
@RunWith(AndroidJUnit4::class)
class WordEnrichCacheE2ETest {

    private lateinit var db: AppDatabase
    private lateinit var mlkit: MlkitOfflineTranslator
    private lateinit var cache: CacheRepository

    private val offlineStub = object : OnlineTranslator {
        override suspend fun lookup(request: LookupRequest): TranslationOutcome =
            TranslationOutcome.Failure(HttpErrors.E4, FailureReason.NO_KEY)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        mlkit = MlkitOfflineTranslator(SettingsStore.create(ApplicationProvider.getApplicationContext()))
        cache = CacheRepository(db)
    }

    @After
    fun tearDown() {
        mlkit.close()
        db.close()
    }

    private fun pipeline() = LookupPipeline(
        online = offlineStub,
        offline = mlkit,
        cache = cache,
        history = null,
        dictionary = FreeDictionaryApi(),
    )

    @Test
    fun enrichThenCacheHit_keepsEnglishDefinitionsAndSynonyms() = runBlocking {
        assertTrue("model ML Kit phải đã tải", mlkit.isModelReady())

        val first = pipeline().lookup("prudent", source = vn.bubbledict.domain.model.TextSource.SELECTION)
        assertEquals(ResultState.OK, first.state)
        assertEquals(TextKind.WORD, first.kind)

        val enriched = pipeline().enrichWord(first)

        assertNotNull("từ điển phải trả dữ liệu cho 'prudent' (nếu upstream 5xx thì test này fail do mạng)", enriched)
        enriched!!
        assertTrue("phải có ít nhất 1 định nghĩa EN", enriched.englishDefinitions.isNotEmpty())
        assertEquals("mlkit+dictionaryapi", enriched.provider)

        // Lượt 2: cache-hit, không gọi ML Kit/từ điển, nhưng vẫn phải có defs/synonyms.
        val second = pipeline().lookup("prudent", source = vn.bubbledict.domain.model.TextSource.SELECTION)

        assertEquals(ResultState.CACHED, second.state)
        assertEquals(Engine.CACHE, second.engine)
        assertEquals(enriched.englishDefinitions, second.englishDefinitions)
        assertTrue("cache-hit phải giữ synonyms", second.synonyms.isNotEmpty())
    }
}