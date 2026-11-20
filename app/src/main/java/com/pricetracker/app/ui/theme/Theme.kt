package com.pricetracker.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PT {
    val Blue = Color(0xFF3E91FF)
    val Down = Color(0xFF4CD995)   // price fell = good
    val Up = Color(0xFFFF6B6B)
    val Card = Color(0xFF151518)
    val CardHigh = Color(0xFF1E1E22)
}

private val scheme = darkColorScheme(
    primary = PT.Blue, onPrimary = Color.Black,
    primaryContainer = Color(0xFF0B2A52), onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF9DB7DB), onSecondary = Color.Black,
    background = Color.Black, onBackground = Color(0xFFEDEDED),
    surface = Color.Black, onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1C1C20), onSurfaceVariant = Color(0xFFA7A7AE),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0F0F11),
    surfaceContainer = PT.Card, surfaceContainerHigh = PT.CardHigh, surfaceContainerHighest = Color(0xFF26262B),
    outline = Color(0xFF3A3A40), outlineVariant = Color(0xFF26262B), error = PT.Up,
)

private val base = Typography()
private val typo = base.copy(
    headlineLarge = base.headlineLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PriceTrackerTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = typo, shapes = shapes, content = content)
