package com.omni.dj.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Violet = Color(0xFF8E8DE5)
val Mint = Color(0xFFB8E9D6)
val Background = Color(0xFF101017)
val Panel = Color(0xFF1C1C28)
private val colors = darkColorScheme(primary = Violet, onPrimary = Color(0xFF17162F),
    secondary = Mint, background = Background, surface = Background, surfaceContainer = Panel,
    onBackground = Color(0xFFF3F1FC), onSurface = Color(0xFFF3F1FC), onSurfaceVariant = Color(0xFFAAA8BC),
    outline = Color(0xFF3E3B51))
@Composable fun OmniTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = colors, content = content) }
