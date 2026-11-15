package io.github.odomu.honorduo

import android.app.Application
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer
import io.github.odomu.honorduo.ui.render.IconZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The capsule body draws two centre pieces; the three-piece switch adds the third.
 *
 * Placement already stores any number of ids per band, so before the switch the third one
 * was silently kept out of the drawing and out of the width measurement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapsuleThreeCentreTest {

    private fun appearance(three: Boolean, vararg parts: Int): StatusAppearance {
        val cfg = ModuleSettings()
        cfg.enabled = true
        cfg.mergeIcons = true
        cfg.showValue = true
        cfg.showWifi = true
        cfg.trioStyle = Prefs.STYLE_CAPSULE
        cfg.capsuleThreeCentre = three
        cfg.ringZones[IconZones.Zone.CENTRE.ordinal] = parts
        return StatusAppearance(cfg)
    }

    @Test
    fun thirdPieceAppearsOnlyWhenSwitchedOn() {
        val parts = intArrayOf(Prefs.PART_CLOCK, Prefs.PART_WIFI, Prefs.PART_VALUE)
        val off = appearance(false, *parts).capsule(64, false, 3, "5G", 0)
        assertEquals(Prefs.PART_NONE, off.third[IconZones.Zone.CENTRE.ordinal])

        val on = appearance(true, *parts).capsule(64, false, 3, "5G", 0)
        assertEquals(Prefs.PART_VALUE, on.third[IconZones.Zone.CENTRE.ordinal])
    }

    @Test
    fun theTwoGapsAreEqual() {
        StatusState.sClockText = "11:29"
        StatusState.sSignalDbm = -85
        val a = appearance(
            true, Prefs.PART_CLOCK, Prefs.PART_SIGNAL, Prefs.PART_VALUE,
        )
        val stroke = a.ringStroke * 0.53f
        val placed = CapsuleRenderer.threePieceLayout(
            a, Prefs.PART_CLOCK, Prefs.PART_SIGNAL, Prefs.PART_VALUE,
            null, 64, stroke, 20f, 320f,
        )
        val firstGap = placed[2] - (placed[0] + placed[1])
        val secondGap = placed[4] - (placed[2] + placed[3])
        val leftMargin = placed[0] - 20f
        val rightMargin = 320f - (placed[4] + placed[5])
        assertEquals(firstGap, secondGap, 0.01f)
        // The pair's rule, applied to three: the outer pieces keep the edge inset and
        // the spare width is split evenly between the two gaps.
        assertEquals(0f, leftMargin, 0.01f)
        assertEquals(0f, rightMargin, 0.01f)
    }

    @Test
    fun widthGrowsForTheThirdPiece() {
        val parts = intArrayOf(Prefs.PART_CLOCK, Prefs.PART_WIFI, Prefs.PART_VALUE)
        val two = appearance(false, *parts)
        val three = appearance(true, *parts)
        val twoWidth = CapsuleRenderer.requiredCapsuleRight(
            two, two.capsule(64, false, 3, "5G", 0), "5G", 64,
        )
        val threeWidth = CapsuleRenderer.requiredCapsuleRight(
            three, three.capsule(64, false, 3, "5G", 0), "5G", 64,
        )
        assertTrue("three pieces must widen the capsule", threeWidth > twoWidth)
    }
}
