package vn.bubbledict.platform

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AC30 — vị trí bong bóng lưu theo cạnh + tỉ lệ chiều cao, không theo pixel.
 *
 * Mô phỏng màn dọc 1080x2400 / ngang 2400x1080 với status bar 80px, nav bar 120px, bong bóng 56dp.
 */
class BubblePositioningTest {

    private fun portrait() = BubblePositioning.Metrics(
        screenWidth = 1080,
        screenHeight = 2400,
        bubbleSize = 168, // 56dp @ density 3
        topInset = 80 + 12,
        bottomInset = 120 + 12,
        edgeMargin = 12,
    )

    private fun landscape() = BubblePositioning.Metrics(
        screenWidth = 2400,
        screenHeight = 1080,
        bubbleSize = 168,
        topInset = 90,
        bottomInset = 130,
        edgeMargin = 12,
    )

    @Test
    fun default_isRightEdge4dpAnd25PercentFromBottom() {
        val m = portrait()
        assertEquals(m.screenWidth - m.bubbleSize - m.edgeMargin, BubblePositioning.defaultX(m))
        val fromBottom = m.screenHeight * 0.25f
        val expectedY = m.screenHeight - fromBottom.roundToInt() - m.bubbleSize
        assertEquals(expectedY, BubblePositioning.defaultY(m))
    }

    @Test
    fun savedLeftEdge_survivesRotation() {
        // Kéo sang trái trên màn dọc rồi xoay ngang: vẫn là cạnh trái.
        val p = portrait()
        val xLeft = BubblePositioning.minX(p)
        val edge = BubblePositioning.edgeOf(xLeft, p)
        assertEquals(BubblePositioning.Edge.LEFT, edge)

        val l = landscape()
        val restoredX = BubblePositioning.xForEdge(edge, l)
        assertEquals(BubblePositioning.minX(l), restoredX)
    }

    @Test
    fun ratio_roundTrip_keepsRelativeHeight() {
        val p = portrait()
        val minY = BubblePositioning.minY(p)
        val maxY = BubblePositioning.maxY(p)
        val y = minY + (maxY - minY) * 0.35f
        val ratio = BubblePositioning.yToRatio(y.roundToInt(), p)

        val l = landscape()
        val restored = BubblePositioning.yFromRatio(ratio, l)
        val lMin = BubblePositioning.minY(l)
        val lMax = BubblePositioning.maxY(l)
        val restoredRatio = BubblePositioning.yToRatio(restored, l)
        // Sai số ≤1px khi làm tròn, không phụ thuộc kích thước màn hình.
        assertTrue(abs(restoredRatio - ratio) < 0.01f)
        assertTrue(restored in lMin..lMax)
    }

    @Test
    fun clamping_neverEntersStatusOrNavInsets() {
        val m = portrait()
        assertEquals(BubblePositioning.minY(m), BubblePositioning.clampY(-500, m))
        assertEquals(BubblePositioning.maxY(m), BubblePositioning.clampY(999_999, m))
        assertEquals(BubblePositioning.minX(m), BubblePositioning.clampX(-500, m))
        assertEquals(BubblePositioning.maxX(m), BubblePositioning.clampX(999_999, m))
    }

    @Test
    fun ratioOutsideRange_isClamped() {
        val m = portrait()
        assertEquals(BubblePositioning.minY(m), BubblePositioning.yFromRatio(-3f, m))
        assertEquals(BubblePositioning.maxY(m), BubblePositioning.yFromRatio(7f, m))
        assertEquals(0f, BubblePositioning.yToRatio(-100, m))
        assertEquals(1f, BubblePositioning.yToRatio(99_999, m))
    }

    @Test
    fun hitTest_onlyCircleAcceptsTouch_BUG15() {
        // Window 76dp = bóng 56dp + 10dp padding mỗi phía (100px @ density 1.32, bóng 74px).
        val window = 100
        val bubble = 74
        val center = window / 2f // 50
        val radius = bubble / 2f // 37

        assertTrue("tâm phải nhận", BubblePositioning.isInsideBubble(center, center, window, bubble))
        assertTrue("sát mép trong phải nhận", BubblePositioning.isInsideBubble(center + radius - 1f, center, window, bubble))
        assertTrue("đúng bán kính vẫn nhận", BubblePositioning.isInsideBubble(center + radius, center, window, bubble))
        // Ca QA bắt được: tap (498,478) cách tâm 44px -> nằm trong window nhưng NGOÀI bóng.
        assertTrue("44px nằm trong nửa window", 44f < center)
        assertFalse("vành 10dp không được nhận tap", BubblePositioning.isInsideBubble(center + 44f, center - 44f + 44f, window, bubble))
        assertFalse("góc window không nhận", BubblePositioning.isInsideBubble(2f, 2f, window, bubble))
        assertFalse("rìa ngoài không nhận", BubblePositioning.isInsideBubble(99f, 50f, window, bubble))
    }

    @Test
    fun hitTest_ringIsConsumedButInert() {
        // Đúng số đo QA: window 100px, bóng 74px -> bán kính 37px; vành từ 37px đến 50px là vành chết.
        val window = 100
        val bubble = 74
        val center = 50f
        for (ringPx in 38..49) {
            assertFalse(
                "px=$ringPx ngoài bóng nên không được kích hoạt hành động",
                BubblePositioning.isInsideBubble(center + ringPx, center, window, bubble),
            )
        }
    }

    @Test
    fun edgeParsing_onlyAcceptsKnownValues() {
        assertEquals(BubblePositioning.Edge.LEFT, BubblePositioning.parseEdge("left"))
        assertEquals(BubblePositioning.Edge.RIGHT, BubblePositioning.parseEdge("right"))
        assertNull(BubblePositioning.parseEdge(""))
        assertNull(BubblePositioning.parseEdge(null))
        assertNull(BubblePositioning.parseEdge("LEFT "))
        assertEquals("left", BubblePositioning.edgeName(BubblePositioning.Edge.LEFT))
        assertEquals("right", BubblePositioning.edgeName(BubblePositioning.Edge.RIGHT))
    }
}