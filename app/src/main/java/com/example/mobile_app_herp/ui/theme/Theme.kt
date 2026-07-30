package com.example.mobile_app_herp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Brass,
    onPrimary = Ink,
    secondary = Steam,
    onSecondary = Ink,
    tertiary = Moss,
    onTertiary = Ink,
    background = Ink,
    onBackground = Bright,
    surface = Ink,
    onSurface = Bright,
    surfaceVariant = Slate,
    onSurfaceVariant = MutedDark,
    outline = SlateEdge,
    outlineVariant = SlateEdge,
    error = RustDim,
    onError = Ink,
)

private val LightScheme = lightColorScheme(
    primary = BrassDeep,
    onPrimary = Chalk,
    secondary = Steam,
    onSecondary = Chalk,
    tertiary = Moss,
    onTertiary = Chalk,
    background = Enamel,
    onBackground = Ink,
    surface = Enamel,
    onSurface = Ink,
    surfaceVariant = Chalk,
    onSurfaceVariant = MutedLight,
    outline = ChalkEdge,
    outlineVariant = ChalkEdge,
    error = Rust,
    onError = Chalk,
)

/**
 * Status colours don't belong in a Material scheme — `tertiary` is not "in
 * progress", and pressing it into that slot turns every call site into a riddle.
 * They travel separately, resolved per theme.
 */
data class StatusColors(
    val pending: Color,
    val inProgress: Color,
    val done: Color,
    val cancelled: Color,
) {
    fun of(status: String): Color = when (status) {
        "pending" -> pending
        "in_progress" -> inProgress
        "done" -> done
        else -> cancelled
    }
}

private val DarkStatus = StatusColors(Brass, SteamDim, MossDim, MutedDark)
private val LightStatus = StatusColors(BrassDeep, Steam, Moss, MutedLight)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

val statusColors: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current

/**
 * Dynamic colour is deliberately NOT used. It repaints an app in whatever hue
 * the user's wallpaper happens to be — the single biggest reason Compose apps
 * all look alike. Brass is the point here, not a default.
 */
@Composable
fun HerpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography,
            content = content,
        )
    }
}
