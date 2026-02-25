package com.gitflow.android.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

// ─── Purple (Material You baseline) ──────────────────────────
private val PurpleLight = lightColorScheme(
    primary              = Color(0xFF6650A4),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFEADDFF),
    onPrimaryContainer   = Color(0xFF21005D),
    secondary            = Color(0xFF625B71),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary             = Color(0xFF7D5260),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFFFD8E4),
    onTertiaryContainer  = Color(0xFF31111D),
)
private val PurpleDark = darkColorScheme(
    primary              = Color(0xFFD0BCFF),
    onPrimary            = Color(0xFF381E72),
    primaryContainer     = Color(0xFF4F378B),
    onPrimaryContainer   = Color(0xFFEADDFF),
    secondary            = Color(0xFFCCC2DC),
    onSecondary          = Color(0xFF332D41),
    secondaryContainer   = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary             = Color(0xFFEFB8C8),
    onTertiary           = Color(0xFF492532),
    tertiaryContainer    = Color(0xFF633B48),
    onTertiaryContainer  = Color(0xFFFFD8E4),
)

// ─── Blue (Ocean) ─────────────────────────────────────────────
private val BlueLight = lightColorScheme(
    primary              = Color(0xFF0060AB),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFD1E4FF),
    onPrimaryContainer   = Color(0xFF001D36),
    secondary            = Color(0xFF535F70),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary             = Color(0xFF6B5778),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFF3DAFF),
    onTertiaryContainer  = Color(0xFF251431),
)
private val BlueDark = darkColorScheme(
    primary              = Color(0xFF9ECAFF),
    onPrimary            = Color(0xFF003258),
    primaryContainer     = Color(0xFF004880),
    onPrimaryContainer   = Color(0xFFD1E4FF),
    secondary            = Color(0xFFBBC7DB),
    onSecondary          = Color(0xFF253140),
    secondaryContainer   = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary             = Color(0xFFD7BEE6),
    onTertiary           = Color(0xFF3B2948),
    tertiaryContainer    = Color(0xFF523F5F),
    onTertiaryContainer  = Color(0xFFF3DAFF),
)

// ─── Green (Forest) ───────────────────────────────────────────
private val GreenLight = lightColorScheme(
    primary              = Color(0xFF1A6B35),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFB6F2B8),
    onPrimaryContainer   = Color(0xFF00210B),
    secondary            = Color(0xFF506351),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFD2E8D2),
    onSecondaryContainer = Color(0xFF0D1F10),
    tertiary             = Color(0xFF386660),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFBCECE5),
    onTertiaryContainer  = Color(0xFF00201D),
)
private val GreenDark = darkColorScheme(
    primary              = Color(0xFF78DC77),
    onPrimary            = Color(0xFF003914),
    primaryContainer     = Color(0xFF005221),
    onPrimaryContainer   = Color(0xFFB6F2B8),
    secondary            = Color(0xFFB6CCB7),
    onSecondary          = Color(0xFF223524),
    secondaryContainer   = Color(0xFF384B3A),
    onSecondaryContainer = Color(0xFFD2E8D2),
    tertiary             = Color(0xFFA0D0C9),
    onTertiary           = Color(0xFF003733),
    tertiaryContainer    = Color(0xFF1F4E48),
    onTertiaryContainer  = Color(0xFFBCECE5),
)

// ─── Orange (Amber) ───────────────────────────────────────────
private val OrangeLight = lightColorScheme(
    primary              = Color(0xFF8B5000),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFFFDDB3),
    onPrimaryContainer   = Color(0xFF2C1600),
    secondary            = Color(0xFF705C42),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFFBDEBC),
    onSecondaryContainer = Color(0xFF261A06),
    tertiary             = Color(0xFF526145),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFD5E8C2),
    onTertiaryContainer  = Color(0xFF111E08),
)
private val OrangeDark = darkColorScheme(
    primary              = Color(0xFFFFB967),
    onPrimary            = Color(0xFF4A2800),
    primaryContainer     = Color(0xFF6A3C00),
    onPrimaryContainer   = Color(0xFFFFDDB3),
    secondary            = Color(0xFFDEC4A2),
    onSecondary          = Color(0xFF3D2E16),
    secondaryContainer   = Color(0xFF56442B),
    onSecondaryContainer = Color(0xFFFBDEBC),
    tertiary             = Color(0xFFB9CCA8),
    onTertiary           = Color(0xFF26331A),
    tertiaryContainer    = Color(0xFF3B4A2F),
    onTertiaryContainer  = Color(0xFFD5E8C2),
)

// ─── Rose ─────────────────────────────────────────────────────
private val RoseLight = lightColorScheme(
    primary              = Color(0xFF8B1D42),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFFFD9E2),
    onPrimaryContainer   = Color(0xFF3A0015),
    secondary            = Color(0xFF74565E),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151B),
    tertiary             = Color(0xFF7C5637),
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFFFDCC2),
    onTertiaryContainer  = Color(0xFF2E1500),
)
private val RoseDark = darkColorScheme(
    primary              = Color(0xFFFFB1C8),
    onPrimary            = Color(0xFF550025),
    primaryContainer     = Color(0xFF6E1130),
    onPrimaryContainer   = Color(0xFFFFD9E2),
    secondary            = Color(0xFFE3BDC5),
    onSecondary          = Color(0xFF432930),
    secondaryContainer   = Color(0xFF5B3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary             = Color(0xFFEFBD96),
    onTertiary           = Color(0xFF47290D),
    tertiaryContainer    = Color(0xFF603F21),
    onTertiaryContainer  = Color(0xFFFFDCC2),
)

object AppColorTheme {
    const val DYNAMIC = "Dynamic"
    const val PURPLE  = "Purple"
    const val BLUE    = "Blue"
    const val GREEN   = "Green"
    const val ORANGE  = "Orange"
    const val ROSE    = "Rose"

    /** Цвет-пример для свотча в диалоге выбора (светлый вариант каждого пресета) */
    fun previewColor(themeId: String): Color = when (themeId) {
        PURPLE -> Color(0xFF6650A4)
        BLUE   -> Color(0xFF0060AB)
        GREEN  -> Color(0xFF1A6B35)
        ORANGE -> Color(0xFF8B5000)
        ROSE   -> Color(0xFF8B1D42)
        else   -> Color(0xFF6650A4)
    }

    fun getColorScheme(themeId: String, darkTheme: Boolean, context: Context): ColorScheme {
        if (themeId == DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        return if (darkTheme) getDarkScheme(themeId) else getLightScheme(themeId)
    }

    private fun getLightScheme(themeId: String): ColorScheme = when (themeId) {
        BLUE   -> BlueLight
        GREEN  -> GreenLight
        ORANGE -> OrangeLight
        ROSE   -> RoseLight
        else   -> PurpleLight
    }

    private fun getDarkScheme(themeId: String): ColorScheme = when (themeId) {
        BLUE   -> BlueDark
        GREEN  -> GreenDark
        ORANGE -> OrangeDark
        ROSE   -> RoseDark
        else   -> PurpleDark
    }
}
