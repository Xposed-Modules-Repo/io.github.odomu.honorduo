package io.github.odomu.honorduo.ui.view
import android.content.Context

import android.graphics.Canvas
import android.view.View
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconRenderer

internal class OutSignalView(context: Context) : View(context) {
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    var host: StatusState? = null

    var bars: Int = 0
    var dots: Int = -1
    var type: String? = null
    var type2: String? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val a = ModuleConfig.appearance()
        val dual = dots >= 0
        val density = resources.displayMetrics.density
        val lp = layoutParams
        val h = if (lp != null && lp.height > 0) {
            lp.height
        } else {
            val specH = MeasureSpec.getSize(heightMeasureSpec)
            if (specH > 0) specH else IconRenderer.outSignalHeight(a, a.outSignalSize, density, dual)
        }
        val w = if (lp != null && lp.width > 0) {
            lp.width
        } else {
            IconRenderer.outSignalWidth(h, a, type, type2, dual)
        }
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val source = host ?: return
        IconRenderer.drawOutSignal(
            canvas, width, height, bars, dots,
            source.foreground(), ModuleConfig.appearance(), type, type2,
        )
    }
}
