package vn.bubbledict.platform

import kotlin.math.roundToInt

/**
 * AC30 — toán vị trí bong bóng tách khỏi `Service` để test JVM.
 *
 * Vị trí lưu theo **cạnh (trái/phải) + tỉ lệ chiều cao trong safe area**, không lưu pixel.
 * Nhờ vậy xoay màn hình, đổi độ phân giải hay restart vẫn đúng chỗ tương đối.
 *
 * Quy ước `yRatio`: 0 = sát inset trên (status bar + 4dp), 1 = sát inset dưới (nav bar + 4dp).
 */
object BubblePositioning {

    enum class Edge { LEFT, RIGHT }

    data class Metrics(
        val screenWidth: Int,
        val screenHeight: Int,
        val bubbleSize: Int,
        /** Status bar đã cộng 4dp. */
        val topInset: Int,
        /** Navigation bar đã cộng 4dp. */
        val bottomInset: Int,
        /** 4dp theo bảng hằng số §2.1b. */
        val edgeMargin: Int,
    )

    fun minY(m: Metrics): Int = m.topInset

    fun maxY(m: Metrics): Int =
        (m.screenHeight - m.bottomInset - m.bubbleSize).coerceAtLeast(minY(m))

    fun minX(m: Metrics): Int = m.edgeMargin

    fun maxX(m: Metrics): Int =
        (m.screenWidth - m.bubbleSize - m.edgeMargin).coerceAtLeast(minX(m))

    /** §2.2: lần đầu cạnh phải, cách mép phải 4dp. */
    fun defaultX(m: Metrics): Int = maxX(m)

    /** §2.2: lần đầu cách đáy 25% chiều cao màn hình. */
    fun defaultY(m: Metrics): Int {
        val fromBottom = (m.screenHeight * 0.25f).roundToInt()
        return (m.screenHeight - fromBottom - m.bubbleSize).coerceIn(minY(m), maxY(m))
    }

    fun xForEdge(edge: Edge, m: Metrics): Int = when (edge) {
        Edge.LEFT -> minX(m)
        Edge.RIGHT -> maxX(m)
    }

    fun edgeOf(x: Int, m: Metrics): Edge =
        if (x + m.bubbleSize / 2 < m.screenWidth / 2) Edge.LEFT else Edge.RIGHT

    /** Đổi toạ độ pixel đang có sang tỉ lệ 0..1 trong safe area. */
    fun yToRatio(y: Int, m: Metrics): Float {
        val span = (maxY(m) - minY(m)).coerceAtLeast(1)
        return ((y - minY(m)).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Khôi phục pixel từ tỉ lệ; luôn kẹp trong safe area. */
    fun yFromRatio(ratio: Float, m: Metrics): Int {
        val span = (maxY(m) - minY(m)).coerceAtLeast(1)
        return (minY(m) + (ratio.coerceIn(0f, 1f) * span).roundToInt()).coerceIn(minY(m), maxY(m))
    }

    fun clampX(x: Int, m: Metrics): Int = x.coerceIn(minX(m), maxX(m))

    fun clampY(y: Int, m: Metrics): Int = y.coerceIn(minY(m), maxY(m))

    /**
     * BUG-15: chỉ vòng tròn quả bóng nhận chạm. Window lớn hơn bóng [1 padding] mỗi phía để chở
     * shadow, phần vành đó chỉ được nuốt chạm chứ không được kích hoạt tap/kéo.
     *
     * @param localX/localY toạ độ trong view (window), gốc là góc trên-trái window.
     */
    fun isInsideBubble(localX: Float, localY: Float, windowSize: Int, bubbleSize: Int): Boolean {
        val half = windowSize / 2f
        val dx = localX - half
        val dy = localY - half
        val radius = bubbleSize / 2f
        return dx * dx + dy * dy <= radius * radius
    }

    fun edgeName(edge: Edge): String = if (edge == Edge.LEFT) "left" else "right"

    fun parseEdge(name: String?): Edge? = when (name) {
        "left" -> Edge.LEFT
        "right" -> Edge.RIGHT
        else -> null
    }
}