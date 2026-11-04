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
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.TextKind

/** BE-07 DoD (phần cần DB thật): hết TTL -> miss; cap 2000; sweep không đụng lịch sử. */
@RunWith(AndroidJUnit4::class)
class CacheRepositoryTest {

    private lateinit var db: AppDatabase
    private var fakeNow = 1_700_000_000_000L

    private fun repo() = CacheRepository(db, now = { fakeNow })

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun putThenGet_hit() = runBlocking {
        val repo = repo()
        repo.put("ubiquitous", "vi", TextKind.WORD, """{"meaning":"có mặt khắp nơi"}""", Engine.KEY)

        val hit = repo.get(" Ubiquitous ", "vi", TextKind.WORD)
        assertNotNull(hit)
        assertEquals("""{"meaning":"có mặt khắp nơi"}""", hit!!.payloadJson)
        assertEquals(Engine.KEY, hit.engine)
    }

    @Test
    fun expiredEntry_isMissAndDeleted() = runBlocking {
        val repo = repo()
        repo.put("frail", "vi", TextKind.WORD, "{}", Engine.KEY)
        fakeNow += CacheRepository.TTL_MS - 1
        assertNotNull(repo.get("frail", "vi", TextKind.WORD)) // còn 1ms

        fakeNow += 1 // chạm đúng TTL
        assertNull(repo.get("frail", "vi", TextKind.WORD))
        assertEquals(0, repo.count()) // miss phải dọn luôn dòng hết hạn
    }

    @Test
    fun capKeepsNewest2000() = runBlocking {
        val repo = repo()
        repeat(2005) { i ->
            fakeNow += 1
            repo.put("word$i", "vi", TextKind.WORD, "{}", Engine.KEY)
        }

        assertEquals(CacheRepository.MAX_CACHE, repo.count())
        assertNotNull(repo.get("word2004", "vi", TextKind.WORD))
        assertNull(repo.get("word0", "vi", TextKind.WORD))
    }

    @Test
    fun sweep_removesExpiredButKeepsFresh() = runBlocking {
        val repo = repo()
        repo.put("old", "vi", TextKind.WORD, "{}", Engine.KEY)
        fakeNow += CacheRepository.TTL_MS + 1
        repo.put("fresh", "vi", TextKind.WORD, "{}", Engine.KEY)

        repo.sweep()

        assertEquals(1, repo.count())
        assertNotNull(repo.get("fresh", "vi", TextKind.WORD))
    }

    @Test
    fun sameTextDifferentKind_separateEntries() = runBlocking {
        val repo = repo()
        repo.put("quiet", "vi", TextKind.WORD, """{"w":1}""", Engine.KEY)
        repo.put("quiet", "vi", TextKind.SENTENCE, """{"s":2}""", Engine.KEY)

        assertEquals(2, repo.count())
        assertEquals("""{"w":1}""", repo.get("quiet", "vi", TextKind.WORD)!!.payloadJson)
        assertEquals("""{"s":2}""", repo.get("quiet", "vi", TextKind.SENTENCE)!!.payloadJson)
    }
}