package com.dualshield.phone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue600,
    secondary = Ink500,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink900,
    surface = SurfaceLight,
    onSurface = Ink900,
    surfaceVariant = Canvas,
    onSurfaceVariant = Ink500,
    outline = Line,
    outlineVariant = Line,
    error = Red500,
    onError = Color.White,
    errorContainer = Color(0xFFFFF0F2),
    onErrorContainer = Red500,
)

private val DarkColors = darkColorScheme(
    primary = Blue300,
    onPrimary = Color(0xFF0B1330),
    primaryContainer = BlueDarkContainer,
    onPrimaryContainer = Blue300,
    secondary = InkDark300,
    onSecondary = Color(0xFF0B0D12),
    background = CanvasDark,
    onBackground = InkDark50,
    surface = SurfaceDark,
    onSurface = InkDark50,
    surfaceVariant = Color(0xFF1F232D),
    onSurfaceVariant = InkDark300,
    outline = LineDark,
    outlineVariant = LineDark,
    error = Red300,
    onError = Color(0xFF3A0A12),
    errorContainer = Color(0xFF3A1E24),
    onErrorContainer = Red300,
)

/**
 * Semantic colours Material 3 has no slot for.
 *
 * Kept in a composition local rather than referenced directly so a dark-mode value is never
 * accidentally used on a light surface.
 */
data class ShieldColors(
    val protected: Color,
    val protectedContainer: Color,
    val blocked: Color,
    val blockedContainer: Color,
    val heroSurface: Color,
    val onHeroSurface: Color,
    val onHeroSurfaceMuted: Color,
)

val LocalShieldColors = staticCompositionLocalOf {
    ShieldColors(
        protected = Green600,
        protectedContainer = Color(0xFFEDF7F3),
        blocked = Red500,
        blockedContainer = Color(0xFFFFF0F2),
        heroSurface = Color(0xFF151923),
        onHeroSurface = Color.White,
        onHeroSurfaceMuted = Color(0xFFAEB6C5),
    )
}

@Composable
fun DualShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You is on where available; the palette above is the fallback, not a brand lock. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val shieldColors = if (darkTheme) {
        ShieldColors(
            protected = Green300,
            protectedContainer = Color(0xFF13291F),
            blocked = Red300,
            blockedContainer = Color(0xFF32151B),
            heroSurface = Color(0xFF1B1F2B),
            onHeroSurface = Color.White,
            onHeroSurfaceMuted = Color(0xFFA9B1C1),
        )
    } else {
        ShieldColors(
            protected = Green600,
            protectedContainer = Color(0xFFEDF7F3),
            blocked = Red500,
            blockedContainer = Color(0xFFFFF0F2),
            heroSurface = Color(0xFF151923),
            onHeroSurface = Color.White,
            onHeroSurfaceMuted = Color(0xFFAEB6C5),
        )
    }

    CompositionLocalProvider(LocalShieldColors provides shieldColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DualShieldTypography,
            shapes = DualShieldShapes,
            content = content,
        )
    }
}
