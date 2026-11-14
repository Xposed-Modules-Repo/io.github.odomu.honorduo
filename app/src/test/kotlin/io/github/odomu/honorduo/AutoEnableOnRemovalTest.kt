package io.github.odomu.honorduo

import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.render.IconZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement switches a piece on; removal has to put it back the way it was.
 *
 * A reading that was off before it was placed must not come back as an out-of-ring
 * component once it is taken out of the layout, and one the user had switched on must not
 * be switched off by the removal either.
 */
class AutoEnableOnRemovalTest {

    private fun settings(): ModuleSettings {
        val cfg = ModuleSettings()
        cfg.trioStyle = Prefs.STYLE_CAPSULE
        cfg.ringZones = Array(Prefs.ZONE_COUNT) { IntArray(0) }
        return cfg
    }

    @Test
    fun placingSwitchesThePieceOnAndRemovingSwitchesItOff() {
        val cfg = settings()
        assertFalse(cfg.showValue)

        val placed = IconZones.withPiece(cfg, Prefs.ZONE_CENTRE, IconZones.Part.VALUE)
        assertTrue("placing must enable the reading", placed.showValue)
        assertTrue("the switch must be recorded", placed.autoEnabledParts.isNotEmpty())

        val removed = IconZones.withoutAt(placed, Prefs.ZONE_CENTRE, 0)
        assertFalse("removal must restore the off switch", removed.showValue)
        assertEquals("", removed.autoEnabledParts)
    }

    @Test
    fun aSwitchTheUserTurnedOnSurvivesRemoval() {
        val cfg = settings()
        cfg.showValue = true

        val placed = IconZones.withPiece(cfg, Prefs.ZONE_CENTRE, IconZones.Part.VALUE)
        assertEquals("an already-on switch is not recorded", "", placed.autoEnabledParts)

        val removed = IconZones.withoutAt(placed, Prefs.ZONE_CENTRE, 0)
        assertTrue("the user's own switch stays on", removed.showValue)
    }

    @Test
    fun clearingAZoneRestoresItsAutoEnabledPieces() {
        val cfg = settings()
        val placed = IconZones.withPiece(cfg, Prefs.ZONE_LEFT, IconZones.Part.WIFI)
        assertTrue(placed.showWifi)

        val cleared = IconZones.withPiece(placed, Prefs.ZONE_LEFT, null)
        assertFalse("an emptied zone must not leave the arcs switched on", cleared.showWifi)
    }
}
