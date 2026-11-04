package vn.bubbledict.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.data.CacheRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.offline.MlkitOfflineTranslator
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.data.remote.FailureReason
import vn.bubbledict.data.remote.TranslationOutcome
import vn.bubbledict.data.remote.HttpErrors
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind

/**
 * BUG-01 (QA phát hiện): kết quả offline phải ghi cache (spec F8) để AC17 pass.
 * Test tích hợp **thật**: Room in-memory + ML Kit translate đã tải model trên emulator.
 * Online bị ép lỗi IO để đi nhánh offline.
 */
@RunWith(AndroidJUnit4::class)
class PipelineOfflineCacheTest {

    private lateinit var db: AppDatabase
    private lateinit var mlkit: MlkitOfflineTranslator

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        mlkit = MlkitOfflineTranslator(SettingsStore.create(ApplicationProvider.getApplicationContext()))
    }

    @After
    fun tearDown() {
        mlkit.close()
        db.close()
    }

    private fun pipeline(cache: CacheRepository) = LookupPipeline(
        online = object : OnlineTranslator {
            override suspend fun lookup(request: LookupRequest): TranslationOutcome =
                TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
        },
        offline = mlkit,
        cache = cache,
        history = null,
    )

    @Test
    fun offlineLookup_writesCache_secondLookupHitsCache() = runBlocking {
        assertTrue("model ML Kit phải đã tải trên emulator (QA FE đã tải)", mlkit.isModelReady())
        val cache = CacheRepository(db)
        val pipe = pipeline(cache)

        val first = pipe.lookup("perspicacious", source = vn.bubbledict.domain.model.TextSource.SELECTION)

        assertEquals("lượt đầu phải OK/MLKIT, thực tế: $first", ResultState.OK, first.state)
        assertEquals(Engine.MLKIT, first.engine)
        assertEquals("sau lượt offline, bảng cache phải có 1 dòng (BUG-01)", 1, db.cacheDao().count())

        val second = pipe.lookup("perspicacious", source = vn.bubbledict.domain.model.TextSource.SELECTION)

        assertEquals("lượt 2 phải là CACHED, thực tế: $second", ResultState.CACHED, second.state)
        assertEquals(Engine.CACHE, second.engine)
        assertEquals(first.translation, second.translation)
        assertEquals(TextKind.WORD, second.kind)
    }
}