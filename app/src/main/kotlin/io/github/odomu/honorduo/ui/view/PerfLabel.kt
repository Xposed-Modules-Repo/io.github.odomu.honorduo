package io.github.odomu.honorduo.ui.view
import android.content.Context

import android.view.Gravity
import android.widget.TextView
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconGeometry

internal class PerfLabel(context: Context) : TextView(context) {
    var host: StatusState? = null

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        letterSpacing = IconGeometry.VALUE_LETTER_SPACING
    }
}
