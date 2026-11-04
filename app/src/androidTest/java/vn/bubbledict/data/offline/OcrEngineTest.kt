package vn.bubbledict.data.offline

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E9 + E12 trên thiết bị thật (ML Kit OCR on-device):
 * - E9: ảnh đen (app nguồn chặn chụp màn hình) -> OCR không ra chữ -> [OcrEngine.OcrResult.Empty].
 * - E12: ảnh lớn được thu nhỏ về cạnh dài ≤ 2000 px, giữ tỉ lệ.
 */
@RunWith(AndroidJUnit4::class)
class OcrEngineTest {

    private lateinit var engine: OcrEngine

    @Before
    fun setUp() {
        engine = OcrEngine()
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun blackImage_returnsEmpty_E9() = runBlocking {
        val black = Bitmap.createBitmap(540, 1200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val result = withTimeout(20_000) { engine.recognize(black) }
        assertTrue("ảnh đen phải là Empty, nhận được $result", result is OcrEngine.OcrResult.Empty)
        black.recycle()
    }

    @Test
    fun largeImage_downscaledToMaxLongSide_E12() {
        val big = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val scaled = engine.downscale(big)

        assertEquals(OcrEngine.MAX_LONG_SIDE, maxOf(scaled.width, scaled.height))
        assertEquals(
            "tỉ lệ khung phải giữ nguyên",
            big.height * OcrEngine.MAX_LONG_SIDE / big.width,
            scaled.height,
        )
        big.recycle()
        if (scaled !== big) scaled.recycle()
    }

    @Test
    fun smallImage_notResized() {
        val small = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val scaled = engine.downscale(small)
        assertTrue("ảnh nhỏ phải giữ nguyên tham chiếu", scaled === small)
        small.recycle()
    }
}