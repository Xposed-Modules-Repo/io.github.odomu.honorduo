package io.github.odomu.honorduo

import android.app.Application
import android.view.View
import android.widget.TextView
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconManager
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.StatusAppearance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class NativeIconRestorationTest {
    @After
    fun releaseTrackedViews() {
        StatusBarIconManager.handBackAll()
    }

    @Test
    fun masterOffRestoresClockAndBatteryWithoutResolvedLayout() {
        val context = RuntimeEnvironment.getApplication()
        val clock = TextView(context).apply { text = "12:34" }
        val battery = View(context)
        StatusBarIconManager.hide(clock)
        StatusBarIconManager.hide(battery)
        val off = StatusAppearance(ModuleSettings.defaults().also { it.enabled = false })
        StatusBarIconManager.applyIconVisibility(off)
        assertEquals(View.VISIBLE, clock.visibility)
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals("12:34", clock.text.toString())
    }

    @Test
    fun hostHiddenIconMustRemainHiddenAcrossModuleCycle() {
        val context = RuntimeEnvironment.getApplication()
        val invisible = View(context).apply { visibility = View.INVISIBLE }
        val gone = View(context).apply { visibility = View.GONE }
        StatusBarIconManager.hide(invisible)
        StatusBarIconManager.hide(gone)
        StatusBarIconManager.handBackAll()
        assertEquals(View.INVISIBLE, invisible.visibility)
        assertEquals(View.GONE, gone.visibility)
    }

    @Test
    fun secondModuleCycleRecordsNewHostStateNotFirstCycleState() {
        val view = View(RuntimeEnvironment.getApplication())
        StatusBarIconManager.hide(view)
        StatusBarIconManager.release(view)
        assertEquals(View.VISIBLE, view.visibility)
        view.visibility = View.INVISIBLE
        StatusBarIconManager.hide(view)
        StatusBarIconManager.release(view)
        assertEquals(View.INVISIBLE, view.visibility)
        view.visibility = View.GONE
        StatusBarIconManager.release(view)
        assertEquals(View.GONE, view.visibility)
    }
}
