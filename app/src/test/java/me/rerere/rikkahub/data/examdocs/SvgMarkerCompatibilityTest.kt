package me.rerere.rikkahub.data.examdocs

import me.rerere.rikkahub.ui.components.richtext.compatibleSvgMarkers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgMarkerCompatibilityTest {
    @Test
    fun keepsEndOrientationAndMirrorsStartReferences() {
        val result = compatibleSvgMarkers(
            """
            <svg viewBox="0 0 100 50">
              <defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" orient="auto-start-reverse">
                <path d="M0,0 L10,5 L0,10 Z"/>
              </marker></defs>
              <path d="M10,10 L80,10" marker-start="url(#arrow)" marker-end="url(#arrow)"/>
            </svg>
            """.trimIndent(),
        )
        assertFalse(result.contains("auto-start-reverse"))
        assertTrue(result.contains("""id="arrow" viewBox="0 0 10 10" refX="9" refY="5" orient="auto""""))
        assertTrue(result.contains("""id="rikka-svg-reversed-0" refX="1.0" refY="5.0""""))
        assertTrue(result.contains("""transform="rotate(180 5.0 5.0)""""))
        assertTrue(result.contains("""marker-start="url(#rikka-svg-reversed-0)" marker-end="url(#arrow)""""))
    }
}
