package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gemini-style dynamic gradient background.
 *
 * How it works:
 *  1. A base linear gradient (bluish at the top, white at the bottom).
 *  2. A few radialGradient blobs on top (colored center -> transparent edge) which are
 *     inherently soft.
 *  3. Each blob runs on its own independent infinite-animation period, drifting slowly along a
 *     sine/cosine path.
 *
 * It does not rely on Modifier.blur, so it works on all API levels and performs better.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "aurora")

    // One phase per blob (0..2π); different durations -> staggered drift, avoiding a uniform look.
    @Composable
    fun phase(durationMillis: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = label,
    )

    val p1 by phase(14_000, "p1")
    val p2 by phase(17_000, "p2")
    val p3 by phase(20_000, "p3")

    val dark = LocalDarkMode.current
    // Drive the whole effect from the active theme's colour tokens instead of hardcoded blues, so it
    // follows every theme (dynamic colour, the presets, and custom primary/secondary/tertiary themes)
    // and stays correct in light/dark. The base relaxes a subtle primary tint at the top into the
    // theme surface; the blobs ARE the three accent tokens (primary / tertiary / secondary).
    val cs = MaterialTheme.colorScheme
    val baseGradient = arrayOf(
        0.0f to lerp(cs.surface, cs.primary, if (dark) 0.16f else 0.10f),
        0.30f to lerp(cs.surface, cs.primary, if (dark) 0.06f else 0.04f),
        0.60f to cs.surface,
        1.0f to cs.surface,
    )

    // Blob intensity per mode (kept subtle in dark); the colours themselves come from the theme tokens.
    val alphaPrimary = if (dark) 0.34f else 0.42f
    val alphaTertiary = if (dark) 0.26f else 0.34f
    val alphaSecondary = if (dark) 0.28f else 0.36f

    // The colour stops only depend on the theme, so build the lists ONCE here (per recomposition),
    // not inside the Canvas draw lambda — that lambda re-runs every animation frame and would
    // otherwise allocate three fresh List<Color> per frame. Only the radial Brush itself is rebuilt
    // per frame (its center is animated and Brush is immutable), which is inherent to the effect.
    val primaryStops = listOf(cs.primary.copy(alpha = alphaPrimary), Color.Transparent)
    val tertiaryStops = listOf(cs.tertiary.copy(alpha = alphaTertiary), Color.Transparent)
    val secondaryStops = listOf(cs.secondary.copy(alpha = alphaSecondary), Color.Transparent)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colorStops = baseGradient)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Blob radius scales with the longer screen edge; large enough to stay soft.
            val r = maxOf(w, h)

            // Blobs all cluster near the top and fade downward, leaving the lower half clear.
            // Top primary (slow horizontal drift).
            drawBlob(
                center = Offset(w * 0.45f + sin(p1) * w * 0.20f, h * 0.02f + cos(p1) * h * 0.05f),
                radius = r * 0.55f,
                colorStops = primaryStops,
            )
            // Top-left tertiary accent.
            drawBlob(
                center = Offset(w * 0.12f + sin(p2) * w * 0.12f, h * 0.22f + cos(p2) * h * 0.08f),
                radius = r * 0.40f,
                colorStops = tertiaryStops,
            )
            // Top-right secondary.
            drawBlob(
                center = Offset(w * 0.88f + sin(p3) * w * -0.14f, h * 0.08f + cos(p3) * h * 0.06f),
                radius = r * 0.42f,
                colorStops = secondaryStops,
            )
        }

        content()
    }
}

/**
 * Draws one soft blob: colored center fading outward to transparent. [colorStops] (center color ->
 * transparent) is precomputed by the caller and reused across frames; only the animated [center]
 * forces a fresh radial Brush per frame.
 */
private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    colorStops: List<Color>,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = colorStops,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
