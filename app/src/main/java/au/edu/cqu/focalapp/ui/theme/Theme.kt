package au.edu.cqu.focalapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette derived from the CQUniversity style guide PDF metadata swatches.
private val CquDarkGreen = Color(0xFF1E4040)
private val CquHeroGreen = Color(0xFFC6DB5D)
private val CquBlack = Color(0xFF231F20)
private val CquGrey = Color(0xFFBDBFC0)
private val CquLightGrey = Color(0xFFE6E7E8)
private val CquOffWhite = Color(0xFFF7F8F3)
private val CquWhite = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = CquDarkGreen,
    onPrimary = CquWhite,
    primaryContainer = CquHeroGreen,
    onPrimaryContainer = CquDarkGreen,
    secondary = CquHeroGreen,
    onSecondary = CquDarkGreen,
    secondaryContainer = Color(0xFFE8F1BE),
    onSecondaryContainer = CquDarkGreen,
    tertiary = CquBlack,
    onTertiary = CquWhite,
    background = CquWhite,
    onBackground = CquBlack,
    surface = CquOffWhite,
    onSurface = CquBlack,
    surfaceVariant = CquLightGrey,
    onSurfaceVariant = CquDarkGreen,
    outline = CquGrey,
    outlineVariant = CquLightGrey
)

private val DarkColors = darkColorScheme(
    primary = CquHeroGreen,
    onPrimary = CquDarkGreen,
    primaryContainer = Color(0xFF355757),
    onPrimaryContainer = CquWhite,
    secondary = CquHeroGreen,
    onSecondary = CquDarkGreen,
    secondaryContainer = Color(0xFF4D5B28),
    onSecondaryContainer = CquWhite,
    tertiary = CquWhite,
    onTertiary = CquBlack,
    background = Color(0xFF122626),
    onBackground = CquWhite,
    surface = Color(0xFF173030),
    onSurface = CquWhite,
    surfaceVariant = Color(0xFF294444),
    onSurfaceVariant = Color(0xFFDCE1D4),
    outline = Color(0xFF6D8282),
    outlineVariant = Color(0xFF294444)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun FocalAppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
