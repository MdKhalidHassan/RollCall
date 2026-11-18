package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = AcademicNavy,
    onPrimary = Color.White,
    primaryContainer = AcademicIndigoContainer,
    onPrimaryContainer = AcademicOnIndigoContainer,
    secondary = SageEmerald,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = WarmAlabaster,
    onBackground = InkPrimary,
    surface = CrispSurface,
    onSurface = InkPrimary,
    surfaceVariant = SoftPaperVariant,
    onSurfaceVariant = InkSecondary,
    outline = SubtleBorderLight,
    outlineVariant = SubtleBorderLight,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = OnCrimsonErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyAcademic,
    onPrimary = AcademicNavyDark,
    primaryContainer = SkyAcademicContainer,
    onPrimaryContainer = OnSkyAcademicContainer,
    secondary = SageEmeraldLight,
    onSecondary = OnSageContainer,
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = OnSageDarkContainer,
    tertiary = AmberContainer,
    onTertiary = OnAmberContainer,
    background = MidnightSlate,
    onBackground = DarkInkPrimary,
    surface = MidnightCard,
    onSurface = DarkInkPrimary,
    surfaceVariant = MidnightVariant,
    onSurfaceVariant = DarkInkSecondary,
    outline = SubtleBorderDark,
    outlineVariant = SubtleBorderDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val RollCallShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RollCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RollCallTypography,
        shapes = RollCallShapes,
        content = content
    )
}
