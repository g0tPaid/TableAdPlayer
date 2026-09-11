package com.tableadplayer.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF0B1F3A)
private val Gold = Color(0xFFD4A017)
private val Panel = Color(0xFF121826)
private val OnPanel = Color(0xFFE8EEF7)

private val KioskDark: ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1400),
    secondary = Color(0xFF7FB3D5),
    background = Color(0xFF05070A),
    surface = Panel,
    onBackground = OnPanel,
    onSurface = OnPanel,
    surfaceVariant = Navy,
    onSurfaceVariant = Color(0xFFC5D0DE),
)

@Composable
fun TableAdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KioskDark,
        content = content,
    )
}
