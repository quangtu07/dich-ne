package vn.bubbledict.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.data.HistoryRepository
import vn.bubbledict.data.VocabRepository
import vn.bubbledict.data.db.entity.CacheEntry
import vn.bubbledict.data.db.entity.HistoryEntry
import vn.bubbledict.data.db.entity.VocabEntry
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * BE-05 DoD: prune history > 1000; xoá lịch sử không xoá vocab; lưu/đọc đủ kind, source, state, engine.
 * Chạy trên emulator: Room in-memory cần Context thật nên đây là instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var history: HistoryRepository
    private lateinit var vocab: VocabRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        history = HistoryRepository(db)
        vocab = VocabRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(
        text: String,
        createdAt: Long = System.currentTimeMillis(),
        kind: TextKind = TextKind.WORD,
        source: TextSource = TextSource.SELECTION,
        engine: Engine? = Engine.KEY,
        state: ResultState = ResultState.OK,
    ) = HistoryEntry(
        rawText = text,
        normText = text.lowercase(),
        kind = kind,
        source = source,
        resultText = if (state == ResultState.ERROR) null else "nghĩa của $text",
        engine = engine,
        state = state,
        errorCode = if (state == ResultState.ERROR) "E4" else null,
        createdAt = createdAt,
    )

    @Test
    fun insert1005_keepsNewest1000() = runBlocking {
        val base = 1_700_000_000_000L
        repeat(1005) { i -> history.record(entry("word$i", createdAt = base + i)) }

        assertEquals(1000, history.count())
        val recent = history.recent(limit = 1000)
        assertEquals("word1004", recent.first().rawText) // mới nhất
        assertEquals("word5", recent.last().rawText) // word0..word4 đã bị prune
        assertNull(recent.firstOrNull { it.rawText == "word1" })
        assertNull(recent.firstOrNull { it.rawText == "word0" })
    }

    @Test
    fun deletingHistory_keepsVocab() = runBlocking {
        history.record(entry("ubiquitous"))
        vocab.save(VocabEntry(text = "ubiquitous", meaning = "có mặt khắp nơi", savedAt = 1L))

        history.clear()

        assertEquals(0, history.count())
        assertEquals(1, vocab.count())
        assertEquals("có mặt khắp nơi", vocab.find("ubiquitous")?.meaning)
    }

    @Test
    fun roundTrip_allFields() = runBlocking {
        val id = history.record(
            entry(
                text = "ubiquitous",
                kind = TextKind.SENTENCE,
                source = TextSource.OCR,
                engine = Engine.MLKIT,
                state = ResultState.CACHED,
            ),
        )
        val read = history.findById(id)
        assertNotNull(read)
        read!!
        assertEquals(TextKind.SENTENCE, read.kind)
        assertEquals(TextSource.OCR, read.source)
        assertEquals(Engine.MLKIT, read.engine)
        assertEquals(ResultState.CACHED, read.state)
    }

    @Test
    fun errorEntry_hasNullEngine() = runBlocking {
        val id = history.record(
            entry(text = "boom", engine = null, state = ResultState.ERROR),
        )
        val read = history.findById(id)!!
        assertEquals(ResultState.ERROR, read.state)
        assertNull(read.engine)
        assertEquals("E4", read.errorCode)
        assertNull(read.resultText)
    }

    @Test
    fun undoDelete_reinsertsSameId() = runBlocking {
        val id = history.record(entry("frail"))
        val snapshot = history.findById(id)!!
        history.delete(id)
        assertNull(history.findById(id))

        val restoredId = history.undoDelete(snapshot)

        assertEquals(id, restoredId)
        assertEquals(snapshot, history.findById(id))
    }

    @Test
    fun undoDelete_afterOtherInsert_doesNotCollide() = runBlocking {
        val firstId = history.record(entry("first", createdAt = 1000L))
        val snapshot = history.findById(firstId)!!
        history.delete(firstId)
        history.record(entry("second", createdAt = 2000L)) // chiếm id mới, không được trùng firstId

        val restoredId = history.undoDelete(snapshot)

        assertEquals(firstId, restoredId)
        assertEquals(2, history.count())
        assertEquals(1, history.recent().count { it.rawText == "first" })
    }

    @Test
    fun vocabUpsert_sameText_updatesAndKeepsId() = runBlocking {
        val id1 = vocab.save(VocabEntry(text = "quaint", meaning = "kỳ lạ", savedAt = 1L))
        val id2 = vocab.save(VocabEntry(text = "quaint", meaning = "cổ kính", note = "ghi chú", savedAt = 2L))

        assertTrue(id1 > 0)
        assertEquals(id1, id2) // lưu trùng text cập nhật dòng cũ, giữ id
        assertEquals(1, vocab.count())
        assertEquals("cổ kính", vocab.find("quaint")?.meaning)
        assertEquals("ghi chú", vocab.find("quaint")?.note)
    }

    @Test
    fun cache_putGet_pruneAndTtl() = runBlocking {
        val dao = db.cacheDao()
        repeat(5) { i ->
            dao.put(
                CacheEntry(
                    key = "k$i",
                    normText = "text$i",
                    targetLang = "vi",
                    kind = TextKind.WORD,
                    payloadJson = "{}",
                    engine = Engine.KEY,
                    createdAt = 1000L + i,
                ),
            )
        }
        assertEquals("k4", dao.get("k4")?.key)

        dao.pruneTo(3)
        assertEquals(3, dao.count())
        assertEquals("k4", dao.get("k4")?.key)
        assertNull(dao.get("k0"))

        dao.deleteOlderThan(1003L) // xoá mục createdAt < 1003, tức k2 (1002); k3/k4 còn
        assertNull(dao.get("k2"))
        assertNotNull(dao.get("k3"))
        assertNotNull(dao.get("k4"))
        assertEquals(2, dao.count())
    }

    @Test
    fun search_isCaseInsensitiveOnNormText() = runBlocking {
        history.record(entry("Ubiquitous", createdAt = 1L))
        history.record(entry("Frail", createdAt = 2L))

        val hits = history.search("ubi")

        assertEquals(1, hits.size)
        assertEquals("Ubiquitous", hits.single().rawText)
        assertTrue(hits.single().normText.startsWith("ubi"))
    }

    @Test
    fun updateProvider_afterEnrich() = runBlocking {
        val id = history.record(entry("ubiquitous", createdAt = 123L))
        assertNull("lượt ghi lúc render chưa có provider", history.findById(id)!!.provider)

        db.historyDao().updateProvider(normText = "ubiquitous", createdAt = 123L, provider = "mlkit+dictionaryapi")

        assertEquals("mlkit+dictionaryapi", history.findById(id)!!.provider)
    }
}
