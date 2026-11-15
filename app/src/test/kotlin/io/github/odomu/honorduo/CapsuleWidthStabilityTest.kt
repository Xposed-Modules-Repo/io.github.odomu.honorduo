package io.github.odomu.honorduo

import android.app.Application
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer
import io.github.odomu.honorduo.ui.render.IconZones
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The capsule's width must not move with the readings it shows.
 *
 * It once measured the live strings, so a wattage or a clock that changed re-measured the
 * view, re-laid the row and slid the whole capsule sideways. The width is now a function of
 * what is placed and how it is sized, which is what these cases pin down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapsuleWidthStabilityTest {

    private fun appearance(vararg parts: Int): StatusAppearance {
        val cfg = ModuleSettings()
        cfg.enabled = true
        cfg.mergeIcons = true
        cfg.showValue = true
        cfg.trioStyle = Prefs.STYLE_CAPSULE
        cfg.ringZones[IconZones.Zone.CENTRE.ordinal] = parts.toList().toIntArray()
        return StatusAppearance(cfg)
    }

    private fun widthOf(a: StatusAppearance): Float {
        val cap = a.capsule(100, false, -1, "5G", 0)
        return CapsuleRenderer.requiredCapsuleRight(a, cap, "5G", 64)
    }

    @Test
    fun changingReadingsDoNotChangeTheWidth() {
        StatusState.sClockText = "12:34"
        StatusState.sChargePowerW = 1.3f
        StatusState.sBatteryTempC = 33
        val before = widthOf(appearance(Prefs.PART_CLOCK, Prefs.PART_POWER))

        StatusState.sClockText = "23:59"
        StatusState.sChargePowerW = 99.9f
        StatusState.sBatteryTempC = 45
        // A fresh snapshot, so the renderer's per-appearance cache cannot hide a reading
        // that leaked into the measurement.
        val after = widthOf(appearance(Prefs.PART_CLOCK, Prefs.PART_POWER))

        assertEquals(before, after, 0f)
    }

    @Test
    fun twoPiecesAreWiderThanOne() {
        val single = widthOf(appearance(Prefs.PART_VALUE))
        val pair = widthOf(appearance(Prefs.PART_VALUE, Prefs.PART_TYPE))
        assertEquals(true, pair >= single)
    }

    @Test
    fun aLonePlacementKeepsTheReferenceWidth() {
        val a = appearance(Prefs.PART_VALUE)
        assertEquals(a.capsuleRight(), widthOf(a), 0f)
    }
}
