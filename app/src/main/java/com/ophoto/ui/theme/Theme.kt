package com.ophoto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OpticalDarkColorScheme = darkColorScheme(
    primary = BrassGold,
    onPrimary = ObsidianBg,
    primaryContainer = ControlPillBg,
    onPrimaryContainer = BrassGold,
    secondary = ShutterOrange,
    onSecondary = TextPrimary,
    tertiary = OpticalEmerald,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ViewfinderSurface,
    onSurface = TextPrimary,
    surfaceVariant = InstrumentDeck,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    error = AlertCrimson,
)

@Composable
fun OphotoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OpticalDarkColorScheme,
        typography = OphotoTypography,
        content = content,
    )
}
