package com.shakeit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette for ShakeIT.
 *
 * Every value below is a 1:1 port of a CSS custom property declared in
 * `debug/mockup/shakeit-prototype.html` (the `:root` block for light and the
 * `:root[data-theme="dark"]` block for dark). The names are kept close to the
 * prototype so the two files can be diffed against each other.
 */
@Immutable
data class ShakeItColors(
    /** `--bg` */
    val background: Color,
    /** `--surface` */
    val surface: Color,
    /** `--surface-2` */
    val surface2: Color,
    /** `--on-surface` */
    val onSurface: Color,
    /** `--on-var` */
    val onSurfaceVariant: Color,
    /** `--outline` */
    val outline: Color,
    /** `--primary` */
    val primary: Color,
    /** `--on-primary` */
    val onPrimary: Color,
    /** `--accent` — the "torch is ON" colour. */
    val accent: Color,
    /** `--on-accent` */
    val onAccent: Color,
    /** `--glow`, kept as a colour; applied with alpha for the blob drop-shadow. */
    val glow: Color,
    /** `--track-off` */
    val trackOff: Color,
    /** `--track-on` */
    val trackOn: Color,
    /** The green dot in the status pill. Not themed in the prototype. */
    val statusActive: Color,
    val isLight: Boolean,
)

/** Light palette — `:root { ... }` */
val LightShakeItColors = ShakeItColors(
    background = Color(0xFFF6F1FB),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECE3F8),
    onSurface = Color(0xFF1C1A20),
    onSurfaceVariant = Color(0xFF68626F),
    outline = Color(0xFFDED4EC),
    primary = Color(0xFF473C70),
    onPrimary = Color(0xFFF5EEFF),
    accent = Color(0xFFFFB238),
    onAccent = Color(0xFF241900),
    glow = Color(0xFFFFB238),
    trackOff = Color(0xFFD9D0E8),
    trackOn = Color(0xFF8B7FC2),
    statusActive = Color(0xFF3DC26B),
    isLight = true,
)

/** Dark palette — `:root[data-theme="dark"] { ... }` */
val DarkShakeItColors = ShakeItColors(
    background = Color(0xFF121016),
    surface = Color(0xFF1C1A22),
    surface2 = Color(0xFF25222F),
    onSurface = Color(0xFFECE5F5),
    onSurfaceVariant = Color(0xFFA79EB5),
    outline = Color(0xFF332F3E),
    primary = Color(0xFF8377BC),
    onPrimary = Color(0xFF1A1630),
    accent = Color(0xFFFFB238),
    onAccent = Color(0xFF241900),
    glow = Color(0xFFFFB238),
    trackOff = Color(0xFF3A3546),
    trackOn = Color(0xFF8377BC),
    statusActive = Color(0xFF3DC26B),
    isLight = false,
)

/**
 * The prototype's switch knob is `#fff` in both themes.
 */
val SwitchKnobColor = Color(0xFFFFFFFF)

internal val LocalShakeItColors = staticCompositionLocalOf { LightShakeItColors }
