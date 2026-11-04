package vn.bubbledict.data.remote

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
import vn.bubbledict.domain.LookupPipeline
import vn.bubbledict.domain.OfflineTranslator
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextSource

/**
 * AC24 thật (cần mạng): **không key + có mạng + đoạn dài** -> vẫn ra bản dịch qua MyMemory,
 * không rơi vào E3b "cần mạng".
 *
 * Test này gọi mạng thật (MyMemory). Offline được stub "chưa có model" để chứng minh
 * kết quả đến từ nguồn keyless online, không phải ML Kit.
 */
@RunWith(AndroidJUnit4::class)
class KeylessChainE2ETest {

    private lateinit var db: AppDatabase

    private val offlineStub = object : OfflineTranslator {
        override suspend fun isModelReady(): Boolean = false
        override suspend fun translate(text: String): String? = null
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun pipeline() = LookupPipeline(
        online = ChainedTranslator(listOf(MyMemoryTranslator())),
        offline = offlineStub,
        cache = CacheRepository(db),
        history = null,
    )

    @Test
    fun longParagraph_noKey_online_translatesViaKeylessSource() = runBlocking {
        val paragraph =
            "The city was quiet. Rain fell all night. By morning the streets were empty. Nobody spoke."

        val result = pipeline().lookup(paragraph, TextSource.SELECTION)

        assertEquals("phải OK, thực tế: $result", ResultState.OK, result.state)
        assertNotNull("phải có bản dịch", result.translation)
        assertTrue(
            "bản dịch phải là tiếng Việt: ${result.translation}",
            result.translation!!.any { it.lowercaseChar() in VIETNAMESE },
        )
        assertEquals(Engine.KEYLESS, result.engine) // MyMemory là nguồn keyless
    }

    @Test
    fun shortSentence_noKey_online_translatesViaKeylessSource() = runBlocking {
        val result = pipeline().lookup("It is raining heavily outside today.", TextSource.SELECTION)

        assertEquals(ResultState.OK, result.state)
        assertTrue(result.translation!!.any { it.lowercaseChar() in VIETNAMESE })
    }

    private companion object {
        val VIETNAMESE =
            "àáảãạăằắẳẵặâầấẩẫậđèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵ".toSet()
    }
}