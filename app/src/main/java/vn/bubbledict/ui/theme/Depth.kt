package vn.bubbledict.ui.theme

import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * UX-P1 — nâng cấp visual "3D nhẹ" (depth / elevation / motion).
 * Nguồn: docs/design/visual-polish-v2.md. Chỉ token + modifier, không asset, không dependency.
 */
object Elevation {
    val flat = 0.dp
    val card = 1.dp
    val sheet = 8.dp
    val bubble = 10.dp
    val overlayMenu = 8.dp
}

/**
 * Motion token. Mọi giá trị đi qua [ms] để tự động về 0 khi người dùng bật
 * "Remove animations" (Accessibility) — spec §2.4 yêu cầu no-op.
 */
object Motion {
    const val pressScale = 0.94f
    const val pressMs = 90
    const val sheetInMs = 240
    const val sheetOutMs = 180
    const val contentMs = 120
    const val contentOffsetDp = 8
    const val tabCrossfadeMs = 120
    const val staggerMs = 18
    const val staggerMax = 6

    val animationsEnabled: Boolean
        get() = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)

    fun ms(value: Int): Int = if (animationsEnabled) value else 0
}

/** §2.2 — gradient bề mặt sheet: chênh 2 đầu ≤6% độ sáng. */
@Composable
fun sheetGradientBrush(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainer,
    ),
)

/** §2.2 — gradient bong bóng (nhẹ hơn primaryContainer ở đầu trên). */
@Composable
fun bubbleGradientBrush(): Brush {
    val top = if (isSystemInDarkTheme()) {
        Color(0xFF3A4E9C)
    } else {
        Color(0xFFE8ECFF)
    }
    return Brush.verticalGradient(
        listOf(top, MaterialTheme.colorScheme.primaryContainer),
    )
}

/**
 * §2.3 — highlight mép trên: 1dp trắng, chỉ ở đoạn giữa của cung bo trên,
 * hai đầu tan dần để không thành "viền kẻ".
 */
@Composable
fun Modifier.sheetTopHighlight(): Modifier {
    val alpha = if (isSystemInDarkTheme()) 0.12f else 0.55f
    val color = Color.White.copy(alpha = alpha)
    return drawWithContent {
        drawContent()
        val inset = 18.dp.toPx()
        if (size.width > inset * 2) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, color, Color.Transparent),
                    startX = inset,
                    endX = size.width - inset,
                ),
                start = Offset(inset, 1.dp.toPx()),
                end = Offset(size.width - inset, 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

/** §2.3 — hairline 1dp trên đỉnh NavigationBar / TopAppBar. */
@Composable
fun Modifier.topHairline(): Modifier {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    return drawWithContent {
        drawContent()
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}