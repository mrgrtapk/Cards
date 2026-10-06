package app.kompakt.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.kompakt.flashcards.ui.FlashcardsRoot
import com.mudita.mmd.ThemeMMD

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

/**
 * Mudita's e-ink palette (same values as MMD's eInkColorScheme), but with every color
 * role filled in. MMD leaves the "surface container" roles undefined, and newer
 * versions of the Material top app bar paint with one of them, which crashes on launch.
 *
 * [ink] is the text color and [paper] the background: black on white normally, and the
 * two swapped for dark mode, so every screen inverts exactly.
 */
private fun eInkColors(ink: Color, paper: Color) = lightColorScheme(
    primary = ink,
    onPrimary = paper,
    primaryContainer = ink,
    onPrimaryContainer = paper,
    inversePrimary = paper,
    secondary = paper,
    onSecondary = ink,
    secondaryContainer = ink,
    onSecondaryContainer = paper,
    tertiary = paper,
    onTertiary = ink,
    tertiaryContainer = ink,
    onTertiaryContainer = paper,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = paper,
    onSurfaceVariant = ink,
    surfaceTint = paper,
    inverseSurface = paper,
    inverseOnSurface = ink,
    error = ink,
    onError = paper,
    errorContainer = paper,
    onErrorContainer = ink,
    outline = ink,
    outlineVariant = ink,
    scrim = ink,
    surfaceBright = paper,
    surfaceDim = paper,
    surfaceContainer = paper,
    surfaceContainerHigh = paper,
    surfaceContainerHighest = paper,
    surfaceContainerLow = paper,
    surfaceContainerLowest = paper,
)

private val LightColors = eInkColors(ink = Black, paper = White)
private val DarkColors = eInkColors(ink = White, paper = Black)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FlashcardsApp
        applySystemBars(app.settings.settings.value.darkMode)
        setContent {
            val settings = app.settings.settings.collectAsState().value
            val dark = settings.darkMode
            // Draw behind the status/navigation bars (matching the page, with icons in the
            // opposite color) so the app can see the keyboard and hide the bottom navigation.
            LaunchedEffect(dark) { applySystemBars(dark) }
            // Mudita Mindful Design theme: black/white e-ink palette, Lato type, no ripples.
            val colors = if (dark) DarkColors else LightColors
            ThemeMMD(colorScheme = colors) {
                // Mudita's text and icons fall back to the "content color", which is black unless
                // something sets it — so set it to the page's ink color for dark mode.
                // Display size: scaling the density enlarges everything in Cards together —
                // text, icons, buttons, and spacing — like Android's own display size setting.
                val base = LocalDensity.current
                val scale = settings.displayScale / 100f
                CompositionLocalProvider(
                    LocalContentColor provides colors.onSurface,
                    LocalDensity provides Density(base.density * scale, base.fontScale),
                ) {
                    FlashcardsRoot(app.store, app.settings)
                }
            }
        }
    }

    private fun applySystemBars(dark: Boolean) {
        // Fully transparent bars: the page itself shows behind the status bar and the
        // navigation bar / gesture handle, with icons in the opposite color.
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        // Android otherwise lays a translucent "contrast" scrim behind the navigation bar.
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
}
