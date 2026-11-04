package vn.bubbledict.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Nguồn: docs/design/mvp-ui.md mục 2.1 (light) và 2.2 (dark).
// FE không tự pha thêm màu; thiếu role nào thì dùng role gần nhất trong bảng.

internal val Primary = Color(0xFF4A5FA5)
internal val OnPrimary = Color(0xFFFFFFFF)
internal val PrimaryContainer = Color(0xFFDCE1FF)
internal val OnPrimaryContainer = Color(0xFF05164D)
internal val Secondary = Color(0xFF5B5D72)
internal val OnSecondary = Color(0xFFFFFFFF)
internal val SecondaryContainer = Color(0xFFE0E0F9)
internal val OnSecondaryContainer = Color(0xFF181A2C)
internal val Surface = Color(0xFFFDFBF7)
internal val OnSurface = Color(0xFF1B1B1F)
internal val SurfaceContainer = Color(0xFFF2F0EB)
internal val SurfaceContainerHigh = Color(0xFFECE9E3)
internal val OnSurfaceVariant = Color(0xFF46464F)
internal val Outline = Color(0xFF767680)
internal val OutlineVariant = Color(0xFFC7C5D0)
internal val Error = Color(0xFFB3261E)
internal val OnError = Color(0xFFFFFFFF)
internal val ErrorContainer = Color(0xFFF9DEDC)
internal val OnErrorContainer = Color(0xFF410E0B)
internal val Success = Color(0xFF2E6B3F)
internal val SuccessContainer = Color(0xFFD7F0DD)
internal val Warning = Color(0xFF7A5200)
internal val WarningContainer = Color(0xFFFFE8B3)

internal val DarkPrimary = Color(0xFFB8C4FF)
internal val DarkOnPrimary = Color(0xFF13296B)
internal val DarkPrimaryContainer = Color(0xFF2C4187)
internal val DarkOnPrimaryContainer = Color(0xFFDCE1FF)
internal val DarkSecondary = Color(0xFFC4C4DC)
internal val DarkOnSecondary = Color(0xFF2D2E42)
internal val DarkSecondaryContainer = Color(0xFF43445A)
internal val DarkOnSecondaryContainer = Color(0xFFE0E0F9)
internal val DarkSurface = Color(0xFF131217)
internal val DarkOnSurface = Color(0xFFE5E1E9)
internal val DarkSurfaceContainer = Color(0xFF1F1E23)
internal val DarkSurfaceContainerHigh = Color(0xFF2A282E)
internal val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
internal val DarkOutline = Color(0xFF90909A)
internal val DarkOutlineVariant = Color(0xFF46464F)
internal val DarkError = Color(0xFFF2B8B5)
internal val DarkOnError = Color(0xFF601410)
internal val DarkErrorContainer = Color(0xFF8C1D18)
internal val DarkOnErrorContainer = Color(0xFFF9DEDC)
internal val DarkSuccess = Color(0xFF8FD6A1)
internal val DarkSuccessContainer = Color(0xFF22452C)
internal val DarkWarning = Color(0xFFFFC66B)
internal val DarkWarningContainer = Color(0xFF4A3500)

/** Màu trạng thái M3 không có sẵn; Designer đã chốt cặp tương phản trong mvp-ui.md §2.1–2.2. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(
    success = Success,
    onSuccess = Color(0xFFFFFFFF),
    successContainer = SuccessContainer,
    onSuccessContainer = Color(0xFF0B2E14),
    warning = Warning,
    onWarning = Color(0xFFFFFFFF),
    warningContainer = WarningContainer,
    onWarningContainer = Color(0xFF261900),
)

internal val DarkExtendedColors = ExtendedColors(
    success = DarkSuccess,
    onSuccess = Color(0xFF0B2E14),
    successContainer = DarkSuccessContainer,
    onSuccessContainer = Color(0xFFD7F0DD),
    warning = DarkWarning,
    onWarning = Color(0xFF261900),
    warningContainer = DarkWarningContainer,
    onWarningContainer = Color(0xFFFFE8B3),
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

internal val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Secondary,
    onTertiary = OnSecondary,
    tertiaryContainer = SecondaryContainer,
    onTertiaryContainer = OnSecondaryContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F5F1),
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE6E3DD),
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = DarkPrimary,
    scrim = Color(0xFF000000),
)

internal val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkSecondary,
    onTertiary = DarkOnSecondary,
    tertiaryContainer = DarkSecondaryContainer,
    onTertiaryContainer = DarkOnSecondaryContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0D0C11),
    surfaceContainerLow = Color(0xFF19181D),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF353339),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    inverseSurface = Color(0xFFE5E1E9),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Primary,
    scrim = Color(0xFF000000),
)