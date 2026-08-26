package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = RustRed,
    secondary = DullSteel,
    tertiary = DriedBlood,
    background = MatteBlack,
    surface = MatteBlack,
    onPrimary = TerminalText,
    onSecondary = TerminalText,
    onTertiary = TerminalText,
    onBackground = AshGray,
    onSurface = AshGray
  )

private val LightColorScheme = DarkColorScheme // Force dark theme for gritty feel

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Enforce dark theme
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve art direction
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
