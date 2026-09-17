package com.example.pmmovil.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PmmColorScheme = lightColorScheme(
    primary              = PmmBlue,
    onPrimary            = Color.White,
    primaryContainer     = PmmNavyVariant,
    onPrimaryContainer   = PmmNavyDark,

    secondary            = PmmBlueLight,
    onSecondary          = Color.White,
    secondaryContainer   = PmmNavyVariant,
    onSecondaryContainer = PmmNavyDark,

    tertiary             = PmmGreen,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFDCFCE7),
    onTertiaryContainer  = PmmGreenDark,

    background           = PmmBgPrimary,
    onBackground         = PmmTextPrimary,

    surface              = PmmBgSurface,
    onSurface            = PmmTextPrimary,
    surfaceVariant       = PmmNavyCard,
    onSurfaceVariant     = PmmTextSecondary,

    outline              = PmmNavyBorder,
    outlineVariant       = Color(0xFFF1F5F9),

    error                = PmmRed,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFEDED),
    onErrorContainer     = PmmRedDark,

    scrim                = PmmScrim,
    inverseSurface       = PmmNavyDark,
    inverseOnSurface     = Color.White,
    inversePrimary       = PmmYellow,
)

@Composable
fun PMMovilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PmmColorScheme,
        typography  = Typography,
        content     = content
    )
}
