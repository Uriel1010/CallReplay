/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

private val LegacyDarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = DeepDarkGreen, // Dark text on light-green buttons
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenContainerLight,

    secondary = GreenGrey80,
    onSecondary = DarkGreyGreen,

    tertiary = AccentGreen80,
    onTertiary = AccentGreenDark,

    surface = DarkSurface,
    background = DarkSurface,
    onBackground = OffWhiteText,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceVariant,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = GreenGrey80,
    secondaryContainer = DarkGreyGreen,
    onSecondaryContainer = GreenGrey80,
    outlineVariant = DarkOutlineVariant,
    onSurface = OffWhiteText,
    outline = GreyGreenOutline
)

private val LegacyLightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = White, // White text on dark-green buttons
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = VeryDarkForest,

    secondary = GreenGrey40,
    onSecondary = White,

    surface = LightSurface,
    background = LightSurface,
    onBackground = NearBlackText,
    surfaceContainerLowest = White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceVariant,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    secondaryContainer = LightSurfaceContainerHigh,
    onSecondaryContainer = DarkGreyGreen,
    outlineVariant = LightOutlineVariant,
    onSurface = NearBlackText,
    outline = GreyGreenOutline
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5936AD), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DFFF), onPrimaryContainer = Color(0xFF32156C),
    secondary = Color(0xFF665680), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEE5FF), onSecondaryContainer = Color(0xFF3E2D59),
    tertiary = Color(0xFF246B48), onTertiary = Color.White,
    background = Color(0xFFFAF7FF), onBackground = Color(0xFF171126),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF171126),
    surfaceVariant = Color(0xFFEEE7F7), onSurfaceVariant = Color(0xFF554B66),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF7FF),
    surfaceContainer = Color(0xFFF5EFFF), surfaceContainerHigh = Color(0xFFEEE5FF),
    surfaceContainerHighest = Color(0xFFE9DFFF),
    outline = Color(0xFF81728F), outlineVariant = Color(0xFFE5DCF3)
)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCDB4FF), onPrimary = Color(0xFF2D1458),
    primaryContainer = Color(0xFF42305F), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFD1BFEB), onSecondary = Color(0xFF362744),
    secondaryContainer = Color(0xFF3B2D50), onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF8BD6AC), onTertiary = Color(0xFF003920),
    background = Color(0xFF15111D), onBackground = Color(0xFFF3ECFF),
    surface = Color(0xFF211A2D), onSurface = Color(0xFFF3ECFF),
    surfaceVariant = Color(0xFF392E48), onSurfaceVariant = Color(0xFFCCC0DD),
    surfaceContainerLowest = Color(0xFF110D17), surfaceContainerLow = Color(0xFF1B1525),
    surfaceContainer = Color(0xFF251D32), surfaceContainerHigh = Color(0xFF30253F),
    surfaceContainerHighest = Color(0xFF3A2D4B),
    outline = Color(0xFF9B8AAE), outlineVariant = Color(0xFF493B5C)
)

@Composable
fun ShizuCallRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Dynamic color scheme
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // System Bar icon theme sync
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
