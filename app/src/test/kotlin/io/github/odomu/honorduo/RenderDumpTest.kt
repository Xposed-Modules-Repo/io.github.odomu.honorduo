package io.github.odomu.honorduo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconRenderer
import io.github.odomu.honorduo.ui.render.IconZones
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Local render bench: draws the glyph for a set of scenarios into PNGs so the layout can be
 * inspected without a device or a SystemUI reload.
 *
 * Run just this class, then look in `app/build/scratch/`:
 *
 *     .\gradlew.bat :app:testDebugUnitTest --tests "*RenderDumpTest*"
 *
 * A scenario is one [Scene]: the settings block, the readings the frame draws with, and the
 * canvas size. Add a case to [scenarios] to see a layout; nothing here asserts, so the file
 * is a bench rather than a test. It lives in the build directory so it never litters the
 * tree, and a normal `test` run regenerates it for free.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderDumpTest {

    /** One frame to render: the settings, the readings, and the canvas. */
    private class Scene(
        val name: String,
        val settings: ModuleSettings,
        val level: Int = 84,
        val wifiLevel: Int = -1,
        val mobileLevel: Int = 84,
        val slots: IntArray? = intArrayOf(3, 4),
        val type: String = "5G",
        val width: Int = 360,
        val height: Int = 180,
    )

    private fun scene(name: String, style: Int, block: ModuleSettings.() -> Unit): Scene =
        Scene(name, ModuleSettings().apply {
            enabled = true
            mergeIcons = true
            showValue = true
            trioStyle = style
            block()
        })

    private fun render(scene: Scene) {
        val bitmap = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        IconRenderer.drawInto(
            canvas, scene.width, scene.height, scene.level, false, false, false, false,
            scene.wifiLevel, scene.mobileLevel, scene.slots, scene.type,
            0xFF000000.toInt(), scene.settings, true,
        )
        val out = File("build/scratch/${scene.name}.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The layouts worth looking at: one per arrangement and per mouth content. */
    private fun scenarios(): List<Scene> {
        StatusState.sClockText = "12:34"
        StatusState.sSignalDbm = -92
        StatusState.sBatteryTempC = 29
        StatusState.sChargePowerW = 1.8f
        StatusState.sBluetoothBatteryLevel = 70

        return listOf(
            scene("ring-centre", Prefs.STYLE_RING) {
                ringZones[IconZones.Zone.CENTRE.ordinal] =
                    intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
            },
            scene("ring-clock", Prefs.STYLE_RING) {
                ringZones[IconZones.Zone.TOP.ordinal] = intArrayOf(Prefs.PART_CLOCK)
                ringZones[IconZones.Zone.CENTRE.ordinal] =
                    intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
            },
            scene("ring-stack-arc", Prefs.STYLE_RING) {
                dualSim = true
                showSim1 = true
                showSim2 = true
                ringStackStyle = Prefs.RING_STACK_ARC
                ringZones[IconZones.Zone.BOTTOM.ordinal] =
                    intArrayOf(Prefs.PART_SIM_1, Prefs.PART_SIM_2)
                ringZones[IconZones.Zone.CENTRE.ordinal] =
                    intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
            },
            scene("ring-stack-flat", Prefs.STYLE_RING) {
                dualSim = true
                showSim1 = true
                showSim2 = true
                ringStackStyle = Prefs.RING_STACK_FLAT
                ringZones[IconZones.Zone.TOP.ordinal] =
                    intArrayOf(Prefs.PART_SIM_1, Prefs.PART_SIM_2)
                ringZones[IconZones.Zone.CENTRE.ordinal] =
                    intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
            },
            scene("ring-flank-wifi", Prefs.STYLE_RING) {
                showWifi = true
                ringZones[IconZones.Zone.LEFT.ordinal] = intArrayOf(Prefs.PART_WIFI)
                ringZones[IconZones.Zone.CENTRE.ordinal] =
                    intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
            },
            Scene(
                "capsule-three", ModuleSettings().apply {
                    enabled = true
                    mergeIcons = true
                    showValue = true
                    trioStyle = Prefs.STYLE_CAPSULE
                    capsuleThreeCentre = true
                    ringZones[IconZones.Zone.CENTRE.ordinal] = intArrayOf(
                        Prefs.PART_CLOCK, Prefs.PART_TYPE, Prefs.PART_VALUE,
                    )
                },
            ),
            Scene(
                "capsule-openings", ModuleSettings().apply {
                    enabled = true
                    mergeIcons = true
                    showValue = true
                    trioStyle = Prefs.STYLE_CAPSULE
                    ringZones[IconZones.Zone.TOP.ordinal] = intArrayOf(Prefs.PART_CLOCK)
                    ringZones[IconZones.Zone.CENTRE.ordinal] =
                        intArrayOf(Prefs.PART_TYPE, Prefs.PART_VALUE)
                },
            ),
            Scene(
                "rect", ModuleSettings().apply {
                    enabled = true
                    mergeIcons = true
                    showValue = true
                    showMobile = true
                    trioStyle = Prefs.STYLE_RECT
                },
            ),
        )
    }

    @Test
    fun dump() {
        // Opt-in: `-PrenderDump=true` (see app/build.gradle.kts). A normal test run must
        // not write files, so the bench returns before touching the disk.
        if (System.getProperty("render.dump") != "true") return
        scenarios().forEach(::render)
    }
}
