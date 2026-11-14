package io.github.odomu.honorduo

import android.app.Application
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.OutSignalRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The out-of-ring reading's size math.
 *
 * A zero default margin once divided by itself inside the row gap: the height became
 * NaN, `roundToInt()` threw, and SystemUI restarted as soon as the out views were shown
 * with the trio switched off. These cases pin the sizes to finite values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutSignalSizingTest {

    private fun appearance(margin: Int): StatusAppearance {
        val cfg = ModuleSettings()
        cfg.stackStyle = Prefs.STACK_STYLE_DOTS
        cfg.outSignalMargin = margin
        return StatusAppearance(cfg)
    }

    @Test
    fun dualReadingHasFiniteHeightAtZeroMargin() {
        val a = appearance(margin = 0)
        val height = OutSignalRenderer.outSignalHeight(a, a.outSignalSize, density = 3f, dual = true)
        assertTrue("height must be positive, was $height", height > 0)
        val width = OutSignalRenderer.outSignalWidth(height, a, "5G", "4G", dual = true)
        assertTrue("width must be positive, was $width", width > 0)
    }

    @Test
    fun widerMarginOnlyAddsHeight() {
        val base = OutSignalRenderer.outSignalHeight(appearance(0), 12, 3f, dual = true)
        val wider = OutSignalRenderer.outSignalHeight(appearance(8), 12, 3f, dual = true)
        assertTrue("a wider margin must not shrink the reading: $base -> $wider", wider >= base)
    }

    @Test
    fun loneReadingKeepsItsDpHeight() {
        val a = appearance(margin = 0)
        assertEquals((12 * 3f).toInt(), OutSignalRenderer.outSignalHeight(a, 12, 3f, dual = false))
    }
}
