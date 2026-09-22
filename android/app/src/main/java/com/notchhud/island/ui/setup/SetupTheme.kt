package com.notchhud.island.ui.setup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val dark = darkColorScheme(
    primary = Color(0xFF3D8BFF),
    background = Color(0xFF0D0D10),
    surface = Color(0xFF17171C),
    onBackground = Color(0xFFE8E8EC),
    onSurface = Color(0xFFE8E8EC),
)

private val light = lightColorScheme(primary = Color(0xFF3D8BFF))

@Composable
fun SetupTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else dark.takeIf { true } ?: light, content = content)
}
