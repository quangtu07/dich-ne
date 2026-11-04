package vn.bubbledict.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.domain.model.TtsVoice

/**
 * BE-12 DoD trên emulator: đọc được giọng US (hoặc trả E14 đúng nếu máy thiếu), bấm lần 2 -> dừng.
 * Emulator đã kiểm có en-US/en-GB (TtsProbeActivity của FE).
 */
@RunWith(AndroidJUnit4::class)
class TtsSpeakerTest {

    private lateinit var speaker: TtsSpeaker

    @Before
    fun setUp() {
        speaker = TtsSpeaker(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After
    fun tearDown() {
        speaker.close()
    }

    @Test
    fun reportsVoiceAvailability() = runBlocking {
        val us = speaker.usAvailable()
        val uk = speaker.ukAvailable()

        // Emulator này có giọng Anh; nếu máy khác thiếu thì E14 là kết quả đúng (spec §5.8).
        assertTrue("phải trả lời được câu hỏi có giọng Anh hay không (us=$us, uk=$uk)", us || uk)
    }

    @Test
    fun speakShortText_finishesWithoutError() = runBlocking {
        val outcome = speaker.speak("hello", TtsVoice.US)

        assertTrue("đọc 'hello' phải xong, thực tế: $outcome", outcome.completed)
        assertTrue("không được có mã lỗi, thực tế: ${outcome.errorCode}", outcome.errorCode == null)
    }

    /**
     * AC22: bấm lần 2 khi đang đọc -> stop() không crash, lượt đọc kết thúc.
     * Emulator có thể tổng hợp xong rất nhanh nên không assert "đang đọc dở" ở đây;
     * AC22 (dừng giữa câu) QA kiểm trên máy thật/loa bật.
     */
    @Test
    fun stopIsSafe() = runBlocking {
        // Chờ TTS bind xong trước (emulator mất ~1,3s) để không dính race init.
        speaker.usAvailable()
        val text = "the quick brown fox jumps over the lazy dog ".repeat(5)
        val deferred = async(Dispatchers.Default) { speaker.speak(text, TtsVoice.US) }

        delay(50)
        speaker.stop()
        val outcome = deferred.await()

        // Emulator tổng hợp nhanh nên không assert "dừng giữa câu"; AC22 kiểm trên máy thật.
        assertTrue(
            "speak phải trả về Outcome sau stop, không treo: $outcome",
            outcome.spokenVoice != null || outcome.errorCode != null,
        )
    }
}