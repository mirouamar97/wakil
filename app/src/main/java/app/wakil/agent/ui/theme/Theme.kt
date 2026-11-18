package app.wakil.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.wakil.agent.R

// Tokens from the shared design system: radar green #0e6e53, green-tinted neutrals.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6E53), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2EADF), onPrimaryContainer = Color(0xFF0A3B2C),
    secondary = Color(0xFF4A5E56), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4EFE9), onSecondaryContainer = Color(0xFF15211D),
    tertiary = Color(0xFFA86500), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBEFD9), onTertiaryContainer = Color(0xFF3D2500),
    error = Color(0xFFB4372F), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8E3E1), onErrorContainer = Color(0xFF4A120E),
    background = Color(0xFFF3F5F1), onBackground = Color(0xFF15211D),
    surface = Color(0xFFF3F5F1), onSurface = Color(0xFF15211D),
    surfaceVariant = Color(0xFFE4EFE9), onSurfaceVariant = Color(0xFF5D6B65),
    outline = Color(0xFF8A968F), outlineVariant = Color(0xFFDDE3DC),
    inverseSurface = Color(0xFF2A3531), inverseOnSurface = Color(0xFFEEF2EF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF1EC), surfaceContainerHigh = Color(0xFFE8ECE6),
    surfaceContainerHighest = Color(0xFFE1E6E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3FBF93), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1D3A30), onPrimaryContainer = Color(0xFFB5EBD5),
    secondary = Color(0xFFB0C4BB), onSecondary = Color(0xFF1C2B25),
    secondaryContainer = Color(0xFF1D2C26), onSecondaryContainer = Color(0xFFE7EEE9),
    tertiary = Color(0xFFF0B34A), onTertiary = Color(0xFF3F2800),
    tertiaryContainer = Color(0xFF352A15), onTertiaryContainer = Color(0xFFFBE3B5),
    error = Color(0xFFFF8A80), onError = Color(0xFF5C0F0A),
    errorContainer = Color(0xFF3A1E1C), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513), onBackground = Color(0xFFE7EEE9),
    surface = Color(0xFF0F1513), onSurface = Color(0xFFE7EEE9),
    surfaceVariant = Color(0xFF1D2C26), onSurfaceVariant = Color(0xFF9AA9A2),
    outline = Color(0xFF6B7A73), outlineVariant = Color(0xFF28332F),
    inverseSurface = Color(0xFFE7EEE9), inverseOnSurface = Color(0xFF18201D),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF18201D),
    surfaceContainer = Color(0xFF1B2420), surfaceContainerHigh = Color(0xFF222C28),
    surfaceContainerHighest = Color(0xFF2B3631),
)

/** Cairo (variable font, SIL OFL) for Arabic and Latin text. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Cairo = FontFamily(
    Font(R.font.cairo, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cairo, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cairo, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cairo, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.cairo() = copy(fontFamily = Cairo)
    return Typography(
        displayLarge = base.displayLarge.cairo(), displayMedium = base.displayMedium.cairo(), displaySmall = base.displaySmall.cairo(),
        headlineLarge = base.headlineLarge.cairo(), headlineMedium = base.headlineMedium.cairo(),
        headlineSmall = base.headlineSmall.copy(fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 32.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Cairo, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Cairo, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.cairo(),
        bodyLarge = base.bodyLarge.copy(fontFamily = Cairo, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Cairo, lineHeight = 22.sp),
        bodySmall = base.bodySmall.cairo(),
        labelLarge = base.labelLarge.cairo(), labelMedium = base.labelMedium.cairo(), labelSmall = base.labelSmall.cairo(),
    )
}

@Composable
fun WakilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = typography(),
        content = content,
    )
}
