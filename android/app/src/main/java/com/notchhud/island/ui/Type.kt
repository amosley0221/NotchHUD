package com.notchhud.island.ui

import androidx.compose.ui.text.font.FontFamily

/**
 * The spec asks for IBM Plex Sans/Mono, "or Roboto Flex + Roboto Mono if bundling
 * is undesired". We take the second option: the system sans and mono are already
 * Roboto on every target device, so the APK carries no font binaries and the
 * numerals still line up in a monospaced column.
 *
 * To switch to Plex later, drop the TTFs in res/font and change these two values.
 */
object Fonts {
    val sans: FontFamily = FontFamily.Default
    val mono: FontFamily = FontFamily.Monospace
}
