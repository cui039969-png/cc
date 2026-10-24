/*
 * Theme/window-insets pattern adapted from FrictionFreeDiary's ui/theme/Theme.kt.
 * Copyright (c) 2026 FrictionFree Diary Contributors. MIT; see NOTICE.md.
 * Daily 3 Minutes changes Copyright 2026, Apache-2.0.
 */
package io.github.daily3minutes.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun Daily3Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFADC8FF),
        onPrimary = Color(0xFF002F67),
        primaryContainer = Color(0xFF174477),
        secondaryContainer = Color(0xFF20324C),
        onSecondaryContainer = Color(0xFFD9E6FF),
        surfaceContainer = Color(0xFF1C2027),
        background = Color(0xFF121316),
        surface = Color(0xFF121316),
        onBackground = Color(0xFFE5E2E6),
        onSurface = Color(0xFFE5E2E6),
    ) else lightColorScheme(
        primary = Color(0xFF215DA8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E6FF),
        onPrimaryContainer = Color(0xFF072F62),
        secondaryContainer = Color(0xFFD9E6FF),
        onSecondaryContainer = Color(0xFF072F62),
        surfaceContainer = Color(0xFFF1F5FC),
        background = Color.White,
        surface = Color.White,
        onBackground = Color(0xFF181A1E),
        onSurface = Color(0xFF181A1E),
    )
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
