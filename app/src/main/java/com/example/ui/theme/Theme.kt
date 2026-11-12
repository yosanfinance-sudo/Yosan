package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF2F4F8),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF222836),
    onPrimaryContainer = Color(0xFFF5F5F7),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1C202B),
    onSecondaryContainer = Color(0xFFE5E5EA),
    tertiary = IncomeGreen,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF0D2818),
    onTertiaryContainer = Color(0xFFE8F5E9),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0x14FFFFFF),
    error = ExpenseRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF6E6E73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF00C853),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF0D2818),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0x0F000000),
    error = ExpenseRed,
    onError = Color.White
)

@Composable
fun FinanceManagerTheme(
    themeMode: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode?.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        "SYSTEM" -> darkTheme
        else -> darkTheme
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
