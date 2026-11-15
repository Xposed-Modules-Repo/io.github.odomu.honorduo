package io.github.odomu.honorduo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.CapsuleRenderer
import io.github.odomu.honorduo.ui.render.IconRenderer
import io.github.odomu.honorduo.ui.render.IconZones
import io.github.odomu.honorduo.ui.render.geometry.CapsuleGeometry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The two pieces in the capsule's body must stay apart.
 *
 * The right-hand cell once aligned its ink with `anchor - advance / 2 - bound`, which is a
 * whole advance too far left: the reading landed on top of its neighbour. This renders the
 * stored layout the device uses - type on the left, value on the right - and requires two
 * separate ink clusters with a gap between them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapsuleCentreSpacingTest {

    private fun runs(columns: List<Int>): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        var prev = -1
        for (x in columns) {
            if (start < 0) start = x
            else if (x != prev + 1) { out.add(start..prev); start = x }
            prev = x
        }
        if (start >= 0) out.add(start..prev)
        return out
    }

    @Test
    fun theTwoCentrePiecesDoNotOverlap() {
        val cfg = ModuleSettings()
        cfg.enabled = true
        cfg.mergeIcons = true
        cfg.showValue = true
        cfg.showWifi = true
        cfg.trioStyle = Prefs.STYLE_CAPSULE
        cfg.ringZones[IconZones.Zone.CENTRE.ordinal] =
            intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
        cfg.ringZones[IconZones.Zone.LEFT.ordinal] = intArrayOf(Prefs.PART_WIFI)
        StatusState.sClockText = "12:34"

        val w = 600
        val h = 160
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        IconRenderer.drawInto(
            canvas, w, h, 100, false, false, false, false,
            3, 4, intArrayOf(4, 4), "5G", Color.BLACK, cfg, false,
        )

        // A horizontal band through the text, sampled column by column.
        val mid = h / 2
        val ink = mutableListOf<Int>()
        for (x in 0 until w) {
            var hit = false
            for (y in (mid - 6)..(mid + 6)) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) < 100 && Color.green(p) < 100 && Color.blue(p) < 100) hit = true
            }
            if (hit) ink.add(x)
        }
        val clusters = runs(ink)
        // The outline's own strokes are the first and last clusters; what is left is the
        // two readings, and they have to be two separate blocks.
        val text = clusters.drop(1).dropLast(1)
        assertTrue("expected two text blocks, saw ${clusters.size} runs: $clusters", text.size >= 2)
        val gap = text[1].first - text[0].last
        assertTrue("the two readings overlap (gap $gap): $text", gap >= 2)
    }

    /**
     * The box the view reserves has to be the box the outline inks.
     *
     * A wider box than ink is what puts a blank strip between the glyph and the component
     * beside it - the "left spacing" that grew with every piece added. This renders at the
     * exact width `GlyphView` measures and requires ink at both edges.
     */
    @Test
    fun theReservedWidthIsFullyInked() {
        val cfg = ModuleSettings()
        cfg.enabled = true
        cfg.mergeIcons = true
        cfg.showValue = true
        cfg.showWifi = true
        cfg.trioStyle = Prefs.STYLE_CAPSULE
        cfg.ringZones[IconZones.Zone.CENTRE.ordinal] =
            intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
        cfg.ringZones[IconZones.Zone.LEFT.ordinal] = intArrayOf(Prefs.PART_WIFI)
        val a = StatusAppearance(cfg)
        val wifi = 3
        val cap = a.capsule(100, false, wifi, "5G", 0)
        val growth = CapsuleRenderer.requiredCapsuleRight(a, cap, "5G", 64) - a.capsuleRight()

        val h = 160
        val w = (h * ((CapsuleGeometry.capInkW(a, wifi) + growth) / CapsuleGeometry.capInkBoxH()))
            .roundToInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        IconRenderer.drawInto(
            canvas, w, h, 100, false, false, false, false,
            wifi, 4, intArrayOf(4, 4), "5G", Color.BLACK, cfg, false,
        )
        var left = -1
        var right = -1
        for (x in 0 until w) {
            var hit = false
            for (y in 0 until h) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) < 240 || Color.green(p) < 240 || Color.blue(p) < 240) hit = true
            }
            if (hit) {
                if (left < 0) left = x
                right = x
            }
        }
        assertTrue("no ink rendered", left >= 0)
        // The arcs on the flank start a couple of units inside the box reserved for them,
        // so a few pixels of slack on that side is the drawing, not a measurement error.
        assertTrue("blank strip of $left px on the left of the view (w=$w)", left <= 12)
        assertTrue("blank strip of ${w - 1 - right} px on the right of the view (w=$w)", right >= w - 3)
    }
}
