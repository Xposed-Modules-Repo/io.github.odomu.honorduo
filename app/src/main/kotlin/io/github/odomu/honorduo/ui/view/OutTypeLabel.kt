package io.github.odomu.honorduo.ui.view
import android.content.Context

import android.view.Gravity
import android.widget.TextView
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconGeometry

internal class OutTypeLabel(context: Context) : TextView(context) {
    var host: StatusState? = null
    var suffixScale: Int = -1

    init {
        gravity = Gravity.CENTER
        isSingleLine = true
        includeFontPadding = false
        letterSpacing = IconGeometry.VALUE_LETTER_SPACING
    }
}
