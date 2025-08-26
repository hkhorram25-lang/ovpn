package com.netvor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
	primary = Color(0xFF00E5FF),
	secondary = Color(0xFF80DEEA),
	background = Color(0xFF0E0E10),
	surface = Color(0xFF16171A),
	onPrimary = Color(0xFF00232B)
)

private val LightColors = lightColorScheme(
	primary = Color(0xFF006D77),
	secondary = Color(0xFF00B4D8),
	background = Color(0xFFF7FAFC),
	surface = Color(0xFFFFFFFF),
	onPrimary = Color(0xFFFFFFFF)
)

private val AppTypography = Typography()

@Composable
fun NetvorTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColors else LightColors,
		typography = AppTypography,
		content = content
	)
}

