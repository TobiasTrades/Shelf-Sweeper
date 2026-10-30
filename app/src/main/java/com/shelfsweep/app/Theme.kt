package com.shelfsweep.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// "Appraiser" look: navy ground, gold for value, ivory text, serif figures.
val Navy = Color(0xFF0F1B2D)
val Navy2 = Color(0xFF16263D)
val Stage = Color(0xFF0B1524)
val Line = Color(0xFF23344F)
val Gold = Color(0xFFD4A64A)
val GoldSoft = Color(0x24D4A64A)
val Ivory = Color(0xFFF3EAD8)
val Muted = Color(0xFF8A97AB)
val Good = Color(0xFF8FC4A0)
val Steady = Color(0xFFD9B26A)
val Bad = Color(0xFFE08A7E)
val Check = Color(0xFFA9C3DF)
val CartTag = Color(0xFF2A3A52)
val PaperInk = Color(0xFF0F1B2D)
val PaperMuted = Color(0xFF5B6577)
val PaperGold = Color(0xFF8A6420)

val Serif = FontFamily.Serif
val Mono = FontFamily.Monospace

fun gradeColor(p: Int): Color = when {
    p >= 60 -> Good
    p >= 30 -> Steady
    else -> Bad
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Navy,
            secondary = Check, onSecondary = Navy,
            background = Navy, onBackground = Ivory,
            surface = Navy2, onSurface = Ivory,
            surfaceVariant = Navy2, onSurfaceVariant = Muted,
            outline = Line, error = Bad,
        ),
        content = content,
    )
}
