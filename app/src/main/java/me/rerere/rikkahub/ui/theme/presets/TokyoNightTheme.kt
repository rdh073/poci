package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetTheme

// Tokyo Night (https://github.com/enkia/tokyo-night-vscode-theme). The dark scheme is the canonical
// Tokyo Night "Night" variant — deep navy-indigo base (#1a1b26, never pure black) with the signature
// blue/purple/cyan accents — which fits the 2026 "Dark Mode 2.0 / Electric Twilight" trend (hue-shifted
// dark surfaces, single saturated accents, OLED-friendly). The light scheme is generated from a
// maintainer-supplied accent palette (purple / slate-blue / amber).
val TokyoNightThemePreset by lazy {
    PresetTheme(
        id = "tokyo_night",
        name = {
            Text(stringResource(id = R.string.theme_name_tokyo_night))
        },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

// --- Tokyo Night Day (light) ---
// Generated from the maintainer-supplied accent palette (purple primary / slate-blue secondary / amber
// tertiary) through CustomTheme's Material dynamic-scheme path (the same one an imported custom theme
// uses), so the full light scheme is derived from the three source colours rather than hand-tuned.
private val lightScheme = CustomTheme(
    primaryColorArgb = 4286930618L,
    secondaryColorArgb = 4283260544L,
    tertiaryColorArgb = 4292915538L,
).generateColorScheme(dark = false)

// --- Tokyo Night (dark, the canonical theme) ---
private val primaryDark = Color(0xFF7AA2F7)
private val onPrimaryDark = Color(0xFF16161E)
private val primaryContainerDark = Color(0xFF3D59A1)
private val onPrimaryContainerDark = Color(0xFFD5E0FF)
private val secondaryDark = Color(0xFFBB9AF7)
private val onSecondaryDark = Color(0xFF16161E)
private val secondaryContainerDark = Color(0xFF44355F)
private val onSecondaryContainerDark = Color(0xFFECDCFF)
private val tertiaryDark = Color(0xFF7DCFFF)
private val onTertiaryDark = Color(0xFF16161E)
private val tertiaryContainerDark = Color(0xFF2B5063)
private val onTertiaryContainerDark = Color(0xFFC8EEFF)
private val errorDark = Color(0xFFF7768E)
private val onErrorDark = Color(0xFF16161E)
private val errorContainerDark = Color(0xFF6B2737)
private val onErrorContainerDark = Color(0xFFFFD9DF)
private val backgroundDark = Color(0xFF1A1B26)
private val onBackgroundDark = Color(0xFFC0CAF5)
private val surfaceDark = Color(0xFF1A1B26)
private val onSurfaceDark = Color(0xFFC0CAF5)
private val surfaceVariantDark = Color(0xFF292E42)
private val onSurfaceVariantDark = Color(0xFFA9B1D6)
private val outlineDark = Color(0xFF565F89)
private val outlineVariantDark = Color(0xFF3B4261)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFC0CAF5)
private val inverseOnSurfaceDark = Color(0xFF1A1B26)
private val inversePrimaryDark = Color(0xFF34548A)
private val surfaceDimDark = Color(0xFF16161E)
private val surfaceBrightDark = Color(0xFF292E42)
private val surfaceContainerLowestDark = Color(0xFF16161E)
private val surfaceContainerLowDark = Color(0xFF1A1B26)
private val surfaceContainerDark = Color(0xFF1F2335)
private val surfaceContainerHighDark = Color(0xFF24283B)
private val surfaceContainerHighestDark = Color(0xFF292E42)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)
