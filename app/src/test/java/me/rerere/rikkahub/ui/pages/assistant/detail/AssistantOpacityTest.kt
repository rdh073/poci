package me.rerere.rikkahub.ui.pages.assistant.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Regression for the background-opacity slider that could not be dragged on a comma-decimal locale
 * (e.g. en-ID / id-ID). The old onValueChange did `it.toFixed(2).toFloatOrNull() ?: 1.0f`, but
 * `toFixed` is `String.format` in the DEFAULT locale, so `0.5` became "0,50" which `toFloatOrNull`
 * rejects — every drag fell back to 1.0 and the thumb jumped to 100%. [snapOpacity] rounds numerically.
 */
class AssistantOpacityTest {

    @Test
    fun `snapOpacity rounds to two decimals independent of locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("id-ID"))

            // The fix: numeric rounding, never a locale-formatted string.
            assertEquals(0.5f, snapOpacity(0.5f), 1e-4f)
            assertEquals(0.55f, snapOpacity(0.554f), 1e-4f)
            assertEquals(0.05f, snapOpacity(0.05f), 1e-4f)
            assertEquals(0f, snapOpacity(-0.2f), 1e-4f) // clamped low
            assertEquals(1f, snapOpacity(1.4f), 1e-4f)  // clamped high

            // Proof the bug was real: the old format-then-parse path yields null under this locale,
            // which is what made the slider snap back to 1.0.
            assertNull("%.2f".format(0.5f).toFloatOrNull())
        } finally {
            Locale.setDefault(original)
        }
    }
}
