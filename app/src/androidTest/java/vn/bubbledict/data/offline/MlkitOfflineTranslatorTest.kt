package vn.bubbledict.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.domain.model.OfflineModelState

/**
 * BE-09 DoD(phần máy thật): tải gói ML Kit en→vi, trạng thái ghi vào DataStore, dịch được.
 * Test này cần mạng cho lần tải model đầu (~30MB) và sẽ chậm hơn các test khác.
 * Kiểm airplane mode để QA làm (không tự tắt mạng emulator vì team còn đang test).
 */
@RunWith(AndroidJUnit4::class)
class MlkitOfflineTranslatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun downloadModel_thenTranslateEnglishToVietnamese() = runBlocking {
        val settings = SettingsStore.create(context)
        val translator = MlkitOfflineTranslator(settings)
        try {
            val states = mutableListOf<OfflineModelState>()

            val downloaded = translator.ensureModelDownloaded { states += it }

            assertTrue("tải gói offline phải thành công (cần mạng lần đầu)", downloaded)
            assertTrue("phải báo DOWNLOADING rồi READY, thực tế: $states", states.contains(OfflineModelState.DOWNLOADING))
            assertTrue("phải báo DOWNLOADING rồi READY, thực tế: $states", states.contains(OfflineModelState.READY))
            assertTrue("sau khi tải, isModelReady phải true", translator.isModelReady())

            val vietnamese = translator.translate("hello")

            assertNotNull("phải dịch được offline", vietnamese)
            assertTrue(
                "bản dịch phải là tiếng Việt có dấu, thực tế: $vietnamese",
                vietnamese!!.any { it.lowercaseChar() in VIETNAMESE_CHARS },
            )
        } finally {
            translator.close()
        }
    }

    private companion object {
        val VIETNAMESE_CHARS =
            "àáảãạăằắẳẵặâầấẩẫậđèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵ".toSet()
    }
}