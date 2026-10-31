package io.github.odomu.honorduo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.TextView
import io.github.odomu.honorduo.hook.statusbar.BarClockManager
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconManager
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.view.BarClockView
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusBarRegressionTest {
    private fun renderer(ink: Int, style: Int = Prefs.CLOCK_STYLE_BALANCED): BarClockView {
        return BarClockView(RuntimeEnvironment.getApplication()).apply {
            setPadding(6, 2, 6, 2)
            apply(
                "12:34", "10/31", "星期六", "九月廿二", "晴", "23℃",
                Prefs.WEATHER_STYLE_ICON_TEXT, style, 400, ink,
                0, 0, 0, 0, 24f, 72, 0f, 0f, 2f, 2f, 240
            )
        }
    }

    @Test
    fun clockHonorsExactAndAtMostConstraintsWithPadding() {
        val view = renderer(Color.WHITE)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(72, View.MeasureSpec.EXACTLY)
        )
        assertEquals(240, view.measuredWidth)
        assertEquals(72, view.measuredHeight)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(72, View.MeasureSpec.AT_MOST)
        )
        assertTrue(view.measuredWidth <= 80)
        assertTrue(view.measuredHeight <= 72)
    }

    @Test
    fun colorChangeRepaintsDigitsWithoutChangingGeometry() {
        val view = renderer(Color.WHITE)
        val w = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(72, View.MeasureSpec.EXACTLY)
        view.measure(w, h)
        view.layout(0, 0, 240, 72)
        val before = view.geometryVersion
        val first = Bitmap.createBitmap(240, 72, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(first))
        view.apply(
            "12:34", "10/31", "星期六", "九月廿二", "晴", "23℃",
            Prefs.WEATHER_STYLE_ICON_TEXT, Prefs.CLOCK_STYLE_BALANCED, 400, Color.BLACK,
            0, 0, 0, 0, 24f, 72, 0f, 0f, 2f, 2f, 240
        )
        assertEquals(before, view.geometryVersion)
        val second = Bitmap.createBitmap(240, 72, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(second))
        var white = 0
        var black = 0
        for (y in 0 until 72) for (x in 0 until 240) {
            if (first.getPixel(x, y) == Color.WHITE) white++
            if (second.getPixel(x, y) == Color.BLACK) black++
        }
        assertTrue("white glyph ink must be painted", white > 100)
        assertEquals("same geometry must recolor the same opaque pixels", white, black)
    }

    @Test
    fun twoLineClockFitsTheBarBudgetAndOverflowsTheShorterBox() {
        val view = renderer(Color.WHITE)
        // The native clock's own box is shorter than the bar; 1.0.4 fitted the bar height.
        view.measure(
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(28, View.MeasureSpec.EXACTLY)
        )
        val height =
            BarClockView::class.java.getDeclaredField("blockHeight").apply { isAccessible = true }
        assertTrue("block must fit the bar height budget", height.getFloat(view) <= 72.01f)
        assertEquals("the view box still honours the parent", 28, view.measuredHeight)
        view.apply(
            "12:35", "10/31", "星期六", "九月廿二", "晴", "23℃",
            Prefs.WEATHER_STYLE_ICON_TEXT, Prefs.CLOCK_STYLE_BALANCED, 400, Color.BLACK,
            0, 0, 0, 0, 24f, 72, 0f, 0f, 2f, 2f, 240
        )
        assertTrue(
            "a later tick must keep the same budget instead of shrinking again",
            height.getFloat(view) <= 72.01f
        )
        assertEquals(28, view.measuredHeight)
    }

    @Test
    fun nativeTextScrollDoesNotMoveCustomClockPixelsOrLeakCanvasTransform() {
        val renderer = renderer(Color.WHITE)
        renderer.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(72, View.MeasureSpec.EXACTLY))
        renderer.layout(0, 0, 240, 72)
        val expected = Bitmap.createBitmap(240, 72, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(expected))
        val native = TextView(RuntimeEnvironment.getApplication())
        native.layout(0, 0, 240, 72)
        native.scrollTo(11, 9)
        val actual = Bitmap.createBitmap(240, 72, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(actual)
        canvas.translate(-11f, -9f)
        val before = android.graphics.Matrix().also { canvas.getMatrix(it) }
        BarClockManager.drawRenderer(native, renderer, canvas)
        val after = android.graphics.Matrix().also { canvas.getMatrix(it) }
        assertEquals(before, after)
        assertTrue("native single-line scrolling must not crop or shift the two-line clock", expected.sameAs(actual))
    }

    @Test
    fun handBackConsumesRecordAndDoesNotReplayOldHostVisibility() {
        val host = View(RuntimeEnvironment.getApplication())
        StatusBarIconManager.hide(host)
        assertEquals(View.GONE, host.visibility)
        StatusBarIconManager.release(host)
        assertEquals(View.VISIBLE, host.visibility)
        host.visibility = View.INVISIBLE
        StatusBarIconManager.release(host)
        assertEquals(View.INVISIBLE, host.visibility)
        StatusBarIconManager.hide(host)
        StatusBarIconManager.handBackAll()
        assertEquals(View.INVISIBLE, host.visibility)
    }
}
