package vn.bubbledict.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Theme của app. Không bật dynamic color (Material You) theo yêu cầu thiết kế:
 * chip trạng thái offline/lỗi/đã lưu cần cặp tương phản đoán trước được.
 */
@Composable
fun BubbleDictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BubbleTypography,
            shapes = BubbleShapes,
            content = content,
        )
    }
}

/** Truy cập các màu trạng thái không có slot trong M3 (success/warning). */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current