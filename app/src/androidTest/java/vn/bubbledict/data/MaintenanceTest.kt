package vn.bubbledict.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.entity.HistoryEntry
import vn.bubbledict.data.db.entity.VocabEntry
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * BE-17: dọn khi mở app — xoá history quá 90 ngày + cache quá TTL, giữ từ vựng;
 * "Xoá dữ liệu" xoá history+cache nhưng không xoá vocab (AC12); chạy 2 lần không mất thêm.
 */
@RunWith(AndroidJUnit4::class)
class MaintenanceTest {

    private lateinit var db: AppDatabase
    private lateinit var history: HistoryRepository
    private lateinit var cache: CacheRepository
    private lateinit var vocab: VocabRepository
    private lateinit var maintenance: Maintenance

    private var fakeNow = 1_700_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        history = HistoryRepository(db)
        cache = CacheRepository(db, now = { fakeNow })
        vocab = VocabRepository(db)
        val settings = SettingsStore.create(ApplicationProvider.getApplicationContext())
        maintenance = Maintenance(history, cache, vocab, settings, now = { fakeNow })
    }

    @After
    fun tearDown() = db.close()

    private fun entry(text: String, createdAt: Long) = HistoryEntry(
        rawText = text,
        normText = text,
        kind = TextKind.WORD,
        source = TextSource.SELECTION,
        resultText = "nghĩa",
        engine = Engine.KEY,
        state = ResultState.OK,
        createdAt = createdAt,
    )

    @Test
    fun openApp_deletesOldHistory_keepsFreshAndVocab() = runBlocking {
        history.record(entry("old", fakeNow - 100 * dayMs))
        history.record(entry("fresh", fakeNow - dayMs))
        vocab.save(VocabEntry(text = "ubiquitous", meaning = "có mặt khắp nơi", savedAt = fakeNow))

        val report = maintenance.runOnAppOpen()

        assertEquals(1, report.deletedOldHistory)
        assertEquals(1, history.count())
        assertEquals("fresh", history.recent().single().rawText)
        assertEquals(1, vocab.count())
        assertNotNull(vocab.find("ubiquitous"))
    }

    @Test
    fun openApp_sweepsExpiredCache() = runBlocking {
        cache.put("old", "vi", TextKind.WORD, "{}", Engine.KEY)
        fakeNow += CacheRepository.TTL_MS + 1
        cache.put("fresh", "vi", TextKind.WORD, "{}", Engine.KEY)

        maintenance.runOnAppOpen()

        assertEquals(1, cache.count())
        assertNotNull(cache.get("fresh", "vi", TextKind.WORD))
    }

    @Test
    fun clearUserData_keepsVocab() = runBlocking {
        history.record(entry("word", fakeNow))
        cache.put("word", "vi", TextKind.WORD, "{}", Engine.KEY)
        vocab.save(VocabEntry(text = "frail", meaning = "yếu ớt", savedAt = fakeNow))

        maintenance.clearUserData()

        assertEquals(0, history.count())
        assertEquals(0, cache.count())
        assertEquals(1, vocab.count())
    }

    @Test
    fun runTwice_isIdempotent() = runBlocking {
        history.record(entry("old", fakeNow - 100 * dayMs))
        history.record(entry("fresh", fakeNow))
        vocab.save(VocabEntry(text = "quaint", meaning = "kỳ lạ", savedAt = fakeNow))

        maintenance.runOnAppOpen()
        val second = maintenance.runOnAppOpen()

        assertEquals(0, second.deletedOldHistory)
        assertEquals(1, history.count())
        assertEquals(1, vocab.count())
    }

    @Test
    fun clearVocab_removesAllVocabOnly() = runBlocking {
        history.record(entry("keep", fakeNow))
        vocab.save(VocabEntry(text = "a", meaning = "1", savedAt = fakeNow))
        vocab.save(VocabEntry(text = "b", meaning = "2", savedAt = fakeNow))

        maintenance.clearVocab()

        assertEquals(0, vocab.count())
        assertEquals(1, history.count())
        assertNull(vocab.find("a"))
    }

    @Test
    fun clearHistory_keepsCacheAndVocab() = runBlocking {
        history.record(entry("word", fakeNow))
        cache.put("word", "vi", TextKind.WORD, "{}", Engine.KEY)
        vocab.save(VocabEntry(text = "frail", meaning = "yếu ớt", savedAt = fakeNow))

        maintenance.clearHistory()

        assertEquals(0, history.count())
        assertEquals(1, cache.count())
        assertEquals(1, vocab.count())
    }

    @Test
    fun clearCache_keepsHistoryAndVocab() = runBlocking {
        history.record(entry("word", fakeNow))
        cache.put("word", "vi", TextKind.WORD, "{}", Engine.KEY)
        vocab.save(VocabEntry(text = "frail", meaning = "yếu ớt", savedAt = fakeNow))

        maintenance.clearCache()

        assertEquals(1, history.count())
        assertEquals(0, cache.count())
        assertEquals(1, vocab.count())
    }

    @Test
    fun cacheSizeBytes_sumsPayloadLength() = runBlocking {
        cache.put("a", "vi", TextKind.WORD, "12345", Engine.KEY)
        cache.put("b", "vi", TextKind.WORD, "123", Engine.KEY)

        assertEquals(8L, maintenance.cacheSizeBytes())
    }
}
