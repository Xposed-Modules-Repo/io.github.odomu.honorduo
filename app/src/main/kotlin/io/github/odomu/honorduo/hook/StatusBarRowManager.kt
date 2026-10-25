/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.odomu.honorduo.hook.StatusBarRowManager.PERF_MIN_SIZE_RATIO
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioState
import io.github.odomu.honorduo.render.GlyphView
import io.github.odomu.honorduo.render.TrioGeometry
import io.github.odomu.honorduo.render.TrioRenderer
import java.lang.reflect.Field
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manages status-bar icon rows (`HwSystemIcons`), out-of-ring custom components
 * (signal, perf readouts, fps, network type), capsule collision avoidance, and anti-burn-in stepping.
 */
internal object StatusBarRowManager {

    /** Every row this module has mounted a glyph into. */
    private val ROWS = ArrayList<Row>()
    private val REGEX_FIVE = Regex("(?:five|5bars|5)_(\\d)")
    private val REGEX_SIGNAL = Regex("signal.*_(\\d)")

    private val BURN_IN_CELLS = arrayOf(
        0 to 0, 1 to 0, 1 to 1, 0 to 1,
        -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
    )

    private const val PERF_BREAK = '\n'
    private const val PERF_MIN_SIZE_RATIO = 0.6f

    /**
     * Base size of the watt / temperature readout, in dp before the size slider.
     *
     * Its own number, not the type label's: sharing that one meant the type's slider
     * moved this text, and left this strip with no size control of its own. Tuned down
     * from the type label's 12dp so the shipped strip reads at what used to be 65% of
     * its old size with the slider still at 100%.
     */
    private const val PERF_TEXT_BASE_DP = 7.8f
    private val PERF_PROBE = Paint()

    @Volatile private var sCapsuleRightField: Field? = null
    @Volatile private var sIsCapsuleShownField: Field? = null
    @Volatile private var sIsColoredCapsuleShownField: Field? = null

    fun initFields(rowClass: Class<*>) {
        sCapsuleRightField = Refl.field(rowClass, "mCapsuleRight")
        sIsCapsuleShownField = Refl.field(rowClass, "mIsCapsuleShown")
        sIsColoredCapsuleShownField = Refl.field(rowClass, "mIsColoredCapsuleShown")
    }

    fun rowsCount(): Int = synchronized(ROWS) { ROWS.size }

    fun snapshot(): List<Row> = synchronized(ROWS) { ArrayList(ROWS) }

    fun firstRowResources(): Resources? = snapshot().firstOrNull()?.row?.resources

    /** Registers (or refreshes) the row for a `HwSystemIcons` instance. */
    fun register(row: ViewGroup) {
        if (find(row) != null) return
        val battery = idView(row, "battery") ?: return
        val parent = battery.parent as? ViewGroup ?: return
        val created = Row(row, parent, battery, TrioState(battery))
        synchronized(ROWS) { ROWS.add(created) }
        watchVisibility(row)
        row.post { syncRow(created, true) }
    }

    fun drop(row: ViewGroup) {
        synchronized(ROWS) {
            for (i in ROWS.indices.reversed()) {
                if (ROWS[i].row === row) {
                    val r = ROWS.removeAt(i)
                    r.row.removeCallbacks(r.burnInTask)
                    resetBurnIn(r)
                }
            }
        }
        refreshShown()
    }

    /**
     * Watches one row for the transitions that decide whether the bar is on screen.
     *
     * `isShown` is the answer - it walks the parents' visibility and the window's -
     * but it is a query, so something has to say when to ask it again: a relayout,
     * the window being hidden or shown, and attachment. All three are rare, and
     * every one of them means what the gate tracks actually moved.
     */
    private fun watchVisibility(row: ViewGroup) {
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = refreshShown()

            override fun onViewDetachedFromWindow(v: View) = refreshShown()
        })
        row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshShown() }
        row.viewTreeObserver.addOnWindowVisibilityChangeListener { refreshShown() }
    }

    /** True while any registered row is displayed. Main thread only. */
    private fun anyRowShown(): Boolean = snapshot().any { it.row.isShown }

    private fun refreshShown() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { refreshShown() }
            return
        }
        BarVisibility.update(anyRowShown())
    }

    fun find(row: ViewGroup): Row? {
        synchronized(ROWS) {
            for (r in ROWS) {
                if (r.row === row) return r
            }
        }
        return null
    }

    fun rowOfBattery(battery: View): Row? {
        synchronized(ROWS) {
            for (r in ROWS) {
                if (r.battery === battery) return r
            }
        }
        return null
    }

    fun syncAll(a: TrioAppearance = TrioConfig.appearance(), force: Boolean = true) {
        for (row in snapshot()) {
            row.row.post {
                syncRow(row, force)
                row.row.requestLayout()
                row.row.invalidate()
            }
        }
    }

    /**
     * Brings one row into the state the settings ask for.
     *
     * Idempotent, and called from the layout pass: it never measures or lays a child
     * out by hand (the `LinearLayout` does that), only mounts, removes, sizes and sets
     * visibility.
     *
     * @param force true from a settings change, where a re-measure of the glyph is
     *   wanted even if nothing about the row moved
     */
    fun syncRow(row: Row, force: Boolean) {
        val a = TrioConfig.appearance()
        StatusBarIconManager.resolveIds(row.row.resources)
        if (!a.enabled) {
            unmountGlyph(row)
            unmountOutViews(row)
            StatusBarIconManager.release(row.battery)
            return
        }
        if (!a.glyph) {
            unmountGlyph(row)
            StatusBarIconManager.release(row.battery)
        } else {
            mountGlyph(row)
            StatusBarIconManager.hide(row.battery)
        }
        StatusBarIconManager.settle(row.container, a)
        // The row lays out on every status-bar change, which is the cheap moment to
        // put the bar's own clock back where the arrangement wants it: the suppression
        // hook only sees visibility writes, and a copy re-shown through another path
        // would otherwise sit in the corner until the next settings change.
        StatusBarIconManager.reassertClock(a)
        syncOutViews(row)
        if (force || row.lastStyle != a.style) {
            row.lastStyle = a.style
            row.glyph?.requestLayout()
        }
        if (a.antiBurnIn) {
            row.row.removeCallbacks(row.burnInTask)
            row.row.postDelayed(row.burnInTask, Prefs.BURN_IN_INTERVAL_MS)
        } else {
            row.row.removeCallbacks(row.burnInTask)
            resetBurnIn(row)
        }
    }

    /**
     * Queues one full [syncRow] for this row.
     *
     * Coalesced: the layout pass that asks may run several times before the looper
     * gets to the runnable, and mounting is not something to repeat.
     */
    fun requestSync(row: Row) {
        // A hidden row lays out for nobody: the work below mounts and measures
        // children. The replay on the way back runs a full sync, so nothing is lost.
        if (!BarVisibility.shown) {
            TrioHooks.markRepaintPending()
            return
        }
        synchronized(row) {
            if (row.syncPosted) return
            row.syncPosted = true
        }
        row.row.post {
            synchronized(row) { row.syncPosted = false }
            syncRow(row, false)
        }
    }

    private fun mountGlyph(row: Row) {
        if (row.glyph != null) return
        val glyph = GlyphView(row.row.context).also { it.state = row.state }
        val at = row.container.indexOfChild(row.battery)
        row.container.addView(
            glyph, if (at < 0) row.container.childCount else at,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        row.glyph = glyph
    }

    private fun unmountGlyph(row: Row) {
        val glyph = row.glyph ?: return
        try {
            row.container.removeView(glyph)
        } catch (ignored: Throwable) {
            // a row torn down under us is not worth crashing over.
        }
        row.glyph = null
        row.lastStyle = -1
    }

    /**
     * Mounts, updates or removes the out-of-ring views for one row.
     *
     * They are children of the same `LinearLayout` as the glyph and stand immediately
     * before it, so the row's own measure and layout place them and reserve their
     * width: the gaps are their end margins, which is what keeps a reading from
     * painting over the icon beside it.
     *
     * The mounting order is the user's own arrangement, nearest the battery first.
     * Each view is inserted directly before the glyph, so inserting the nearest one
     * first and walking outwards leaves the outermost view at the far end - the same
     * order the setting lists.
     */
    private fun syncOutViews(row: Row) {
        val a = TrioConfig.appearance()
        for (slot in a.outOrder) {
            when (slot) {
                Prefs.OUT_SLOT_SIGNAL -> syncSignal(row, a)
                Prefs.OUT_SLOT_READOUTS -> {
                    syncPerf(row, a)
                    syncFps(row, a)
                }
                Prefs.OUT_SLOT_TYPE -> syncType(row, a)
                else -> Unit
            }
        }
        reorderOutViews(row, a)
    }

    /**
     * Puts the out-of-ring views in the order the setting asks for.
     *
     * `sync*` only inserts a view the first time it is needed, so changing the order
     * used to do nothing at all: the views stayed where they were first placed. Each
     * one is re-inserted before the glyph in list order, which is what makes the last
     * entry in the list the one nearest the battery.
     */
    private fun reorderOutViews(row: Row, a: TrioAppearance) {
        val order = a.outOrder
        val applied = row.outOrderApplied
        if (applied != null && applied.contentEquals(order)) return
        row.outOrderApplied = order.copyOf()
        for (slot in order.reversed()) {
            val view = when (slot) {
                Prefs.OUT_SLOT_SIGNAL -> row.signal
                Prefs.OUT_SLOT_READOUTS -> row.perf
                Prefs.OUT_SLOT_TYPE -> row.type
                else -> null
            } ?: continue
            if (view.parent !== row.container) continue
            val fps = if (slot == Prefs.OUT_SLOT_READOUTS) row.fps else null
            if (fps?.parent === row.container) {
                row.container.removeView(fps)
            }
            row.container.removeView(view)
            insertBefore(row, view)
            if (fps != null) {
                insertBefore(row, fps)
            }
        }
    }

    /** The stacked SIM reading, or nothing when the signal is handed back. */
    private fun syncSignal(row: Row, a: TrioAppearance) {
        if (!a.stackedOut()) {
            remove(row, row.signal)
            row.signal = null
            return
        }
        val density = row.row.resources.displayMetrics.density
        var signal = row.signal
        if (signal == null) {
            signal = OutSignalView(row.row.context)
            insertBefore(row, signal)
            row.signal = signal
            row.outOrderApplied = null
        }
        signal.host = row.state
        val rowHeight = row.row.height
        val reading = IntArray(2)
        row.state.refresh()
        TrioRenderer.outSignalLevels(
            a.dataSimOnly, row.state.mobileLevel,
            row.state.slotLevels, TrioState.sDataSlot, row.state.sims, reading,
        )
        // Each card's icon name states its level, even when telephony cannot answer.
        val native = nativeSlotLevels(row.container)
        if (native != null && native.size >= 2) {
            // "Data SIM only" still means one row: reading the cards must not quietly
            // undo it by putting the second card back.
            if (a.dataSimOnly) {
                val slot = TrioState.sDataSlot
                if (slot in native.indices) {
                    reading[0] = native[slot]
                }
                reading[1] = -1
            } else {
                reading[0] = native[0]
                reading[1] = native[1]
            }
        }
        // Per-card types; an unanswered card borrows the sampled type so no label goes blank.
        if (reading[1] >= 0 && !a.showSim2) {
            reading[1] = -1
        }
        val fallback = TrioState.sMobileType.ifEmpty { null }
        val dataSlot = TrioState.sDataSlot.coerceIn(0, row.state.slotTypes.size - 1)
        val slots = row.state.slotLevels
        val dual = reading[1] >= 0
        val firstSlot = when {
            a.dataSimOnly -> dataSlot
            dual -> 0
            slots[0] >= 0 -> 0
            else -> dataSlot
        }
        signal.type = row.state.slotTypes[firstSlot].ifEmpty { fallback }
        signal.type2 = if (a.dataSimOnly || !dual) null else row.state.slotTypes[1].ifEmpty { fallback }
        signal.bars = max(0, reading[0])
        signal.dots = reading[1]
        var height = TrioRenderer.outSignalHeight(a, a.outSignalSize, density, dual)
        // The reading is centred in the row, and its caption is drawn flush with its TOP,
        // so anything taller than the row overflows UPWARD first and the bar's top edge
        // eats the caption before it touches the bars. The row's own height is not the
        // bound: it reads 0 before the first layout, and a row can be taller than the part
        // of the bar that is actually visible. The status bar height is the hard bound, so
        // the clamp takes whichever of the two is present and smaller.
        val bar = barHeight(row.row)
        val ceiling = when {
            rowHeight > 0 && bar > 0 -> min(rowHeight, bar)
            rowHeight > 0 -> rowHeight
            else -> bar
        }
        if (ceiling > 0) {
            val maxSafe = (ceiling - 4f * density).toInt()
            if (maxSafe > 0) {
                height = min(height, maxSafe)
            }
        }
        val width = TrioRenderer.outSignalWidth(
            height, a, signal.type, signal.type2, dual,
        )
        val marginDp = if (a.outSignalMargin in 0..16) a.outSignalMargin else Prefs.DEF_OUT_SIGNAL_MARGIN
        size(signal, width, height, marginDp, 0, density)
        StatusBarIconManager.release(signal)
    }

    /**
     * The level of each card in the native signal group, read off its icon.
     *
     * `setIconForView` names neither a SIM nor a slot, and telephony may refuse the
     * answer, but the group itself has one card per SIM and each card's icon is a
     * drawable whose resource name encodes the level (`stat_sys_signal_five_5_small`,
     * `signal_4`…). That is the mapping the status bar is drawing, so it cannot be
     * stale relative to what the user sees.
     *
     * Answered in the module's own 0..4 scale.
     */
    private fun nativeSlotLevels(root: View): IntArray? {
        if (!StatusBarIconManager.idsResolved || StatusBarIconManager.mobileGroupId == 0) return null
        val group = root.findViewById<ViewGroup>(StatusBarIconManager.mobileGroupId)
            ?: StatusBarIconManager.contentsViewGroup?.findViewById(StatusBarIconManager.mobileGroupId)
            ?: return null
        val count = minOf(group.childCount, TrioState.SIM_SLOTS)
        if (count < 2) return null
        val levels = IntArray(count)
        for (i in 0 until count) {
            val card = group.getChildAt(i) as? ViewGroup ?: return null
            levels[i] = levelOfCard(card) ?: return null
        }
        return levels
    }

    /** One card's level, 0..4, or null when its icon cannot be read. */
    private fun levelOfCard(card: ViewGroup): Int? {
        var iconId = 0
        val signalId = StatusBarIconManager.mobileSignalId
        val icon = if (signalId != 0) card.findViewById<View>(signalId) else null
        if (icon is ImageView) {
            val drawable = icon.drawable
            if (drawable != null) {
                iconId = runCatching {
                    drawable.javaClass.getField("iconId").getInt(drawable)
                }.getOrDefault(0)
            }
        }
        if (iconId <= 0) {
            iconId = runCatching {
                card.javaClass.getField("mMobileStrengthId").getInt(card)
            }.getOrDefault(0)
        }
        if (iconId <= 0) return null
        val name = runCatching {
            card.resources.getResourceEntryName(iconId)
        }.getOrNull() ?: return null
        if (name.contains("signal_null")) return 0
        val five = REGEX_FIVE.find(name)
        val raw = if (five != null) {
            five.groupValues[1].toIntOrNull()
        } else {
            REGEX_SIGNAL.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { (it * 5 + 2) / 4 }
        } ?: return null
        return ((raw * 4 + 2) / 5).coerceIn(0, 4)
    }

    /** The charging-power / battery-temperature readout. */
    private fun syncPerf(row: Row, a: TrioAppearance) {
        if (!a.perfAnywhere()) {
            remove(row, row.perf)
            row.perf = null
            return
        }
        val density = row.row.resources.displayMetrics.density
        var perf = row.perf
        if (perf == null) {
            perf = PerfLabel(row.row.context)
            insertBefore(row, perf)
            row.perf = perf
            row.outOrderApplied = null
        }
        perf.host = row.state
        row.state.refresh()
        val text = perfText(a, row.state)
        if (text != perf.text.toString()) {
            perf.text = text
        }
        applyTypeface(perf, a.perfTypeWeight())
        perf.setTextSize(TypedValue.COMPLEX_UNIT_PX, perfTextSize(text, row, a, density))
        // The stacked pair's extra breathing room: the platform adds this to every line
        // beyond the first, so a single line is unaffected and the measured height above
        // already accounts for it.
        perf.setLineSpacing(a.perfLineGap * density, 1f)
        val baseColor = row.state.foreground()
        val tempIsHigh = a.roleColorsTemp && TrioState.sBatteryHot
        if (tempIsHigh) {
            val highColor = if (TrioRenderer.isDarkBackground(baseColor)) a.highTempOnDark else a.highTempOnLight
            val tempStr = "${TrioState.sBatteryTempC}°C"
            val idx = text.indexOf(tempStr)
            if (idx >= 0) {
                val span = SpannableStringBuilder(text)
                span.setSpan(
                    ForegroundColorSpan(highColor),
                    idx, idx + tempStr.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                perf.text = span
            } else {
                perf.text = text
            }
        } else {
            perf.text = text
            perf.setTextColor(baseColor)
        }
        size(perf, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, a.outTypeMarginRight, 0, density)
        if (text.isEmpty()) {
            StatusBarIconManager.hide(perf)
        } else {
            StatusBarIconManager.release(perf)
        }
    }

    /**
     * The frame rate, in its own view beside the readouts.
     *
     * It prefers the readouts strip, which has a second line free whenever only one of the
     * watt and the temperature is switched on. This view exists for the case that strip
     * cannot take it: both lines in use, or no strip at all - then a third line would
     * shrink all three readings, so the frame rate stands beside them instead.
     */
    private fun syncFps(row: Row, a: TrioAppearance) {
        row.state.refresh()
        val text = fpsText(a, row.state)
        if (text.isEmpty()) {
            remove(row, row.fps)
            row.fps = null
            return
        }
        val density = row.row.resources.displayMetrics.density
        var label = row.fps
        if (label == null) {
            label = PerfLabel(row.row.context)
            insertBefore(row, label)
            row.fps = label
            row.outOrderApplied = null
        }
        label.host = row.state
        if (text != label.text.toString()) {
            label.text = text
        }
        applyTypeface(label, a.perfTypeWeight())
        val fpsSizePx = (a.outTypeSize * density * (a.perfScale / 100f)).roundToInt().toFloat()
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, fpsSizePx)
        label.setTextColor(row.state.foreground())
        size(label, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, a.outTypeMarginRight, 0, density)
        StatusBarIconManager.release(label)
    }

    /**
     * The frame rate as its own view draws it: "120", or empty.
     *
     * Empty in two cases: the glyph is drawing the piece itself, or the strip had a free
     * second line and took it - one frame rate, drawn once. It is mounted only when the
     * strip is already full (or empty), which is the case a third line would break.
     */
    private fun fpsText(a: TrioAppearance, s: TrioState): String {
        val fpsWanted = a.perfFps || (!a.glyph && a.hasRingPart(Prefs.PART_FPS))
        if (!fpsWanted || TrioState.sFps <= 0) return ""
        if (a.glyph && a.drawsPart(Prefs.PART_FPS, s.level, s.charging, s.wifiLevel, s.mobileType, s.sims)) {
            return ""
        }
        if (perfStripLines(a) == 1) return ""
        return TrioState.sFps.toString()
    }

    /** The network type, when it is drawn outside the glyph. */
    private fun syncType(row: Row, a: TrioAppearance) {
        if (!a.typeOutOfRing || a.stackTypeShown() || a.labelInReading() ||
            (a.signalOutOfRing && !a.stackedSignal)
        ) {
            remove(row, row.type)
            row.type = null
            return
        }
        val density = row.row.resources.displayMetrics.density
        var label = row.type
        if (label == null) {
            label = OutTypeLabel(row.row.context)
            insertBefore(row, label)
            row.type = label
            row.outOrderApplied = null
        }
        label.host = row.state
        row.state.refresh()
        val text = row.state.mobileType
        if (text.isEmpty()) {
            StatusBarIconManager.hide(label)
            return
        }
        if (text != label.text.toString() || label.suffixScale != a.typeSuffixScale) {
            applyOutTypeText(label, text, a.typeSuffixScale)
        }
        applyTypeface(label, a.typeWeight)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, (a.outTypeSize * density).roundToInt().toFloat())
        label.setTextColor(row.state.foreground())
        size(
            label, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            a.outTypeMarginRight, a.outTypeMarginLeft, density,
        )
        StatusBarIconManager.release(label)
    }

    /** Inserts [view] immediately before the glyph, so the row orders them. */
    private fun insertBefore(row: Row, view: View) {
        val anchor: View = row.glyph ?: row.battery
        val at = row.container.indexOfChild(anchor)
        val lp = view.layoutParams ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        row.container.addView(view, if (at < 0) row.container.childCount else at, lp)
    }

    /**
     * Places one out-of-ring view in the row.
     *
     * The gaps are given in dp and converted here, so the three call sites cannot round
     * them differently. A row adds both sides of a neighbouring pair, which is why every
     * view here sets an end margin and leaves the start at zero except the type label.
     */
    private fun size(view: View, width: Int, height: Int, gapEndDp: Int, gapStartDp: Int, density: Float) {
        val lp = view.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(width, height)
        lp.width = width
        lp.height = height
        lp.gravity = Gravity.CENTER_VERTICAL
        lp.marginEnd = (gapEndDp * density).roundToInt()
        lp.marginStart = (gapStartDp * density).roundToInt()
        view.layoutParams = lp
    }

    private fun remove(row: Row, view: View?) {
        if (view == null) return
        try {
            row.container.removeView(view)
        } catch (ignored: Throwable) {
            // already gone
        }
    }

    private fun unmountOutViews(row: Row) {
        remove(row, row.signal)
        remove(row, row.perf)
        remove(row, row.type)
        remove(row, row.fps)
        row.signal = null
        row.perf = null
        row.type = null
        row.fps = null
        row.outOrderApplied = null
    }

    private fun applyTypeface(view: TextView, weight: Int) {
        val face = TrioRenderer.typefaceFor(weight)
        if (view.typeface !== face) {
            view.typeface = face
        }
    }

    /**
     * Sets the label's text, shrinking a trailing "A" when the user asked for it.
     *
     * A `SpannableStringBuilder` with a [RelativeSizeSpan] rather than two draws: this
     * is a real `TextView`, so the platform lays the runs out and keeps them on one
     * baseline for free.
     */
    private fun applyOutTypeText(label: OutTypeLabel, text: String, scalePercent: Int) {
        if (!TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            label.text = text
        } else {
            val span = SpannableStringBuilder(text)
            val at = text.length - 1
            span.setSpan(
                RelativeSizeSpan(scalePercent / 100f),
                at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            label.text = span
        }
        label.suffixScale = scalePercent
    }

    private fun perfText(a: TrioAppearance, s: TrioState): String {
        if (!a.perfAnywhere()) return ""
        val text = StringBuilder()
        val charging = TrioState.sChargingAttached
        val wattsWanted = charging || a.perfRunning || (!a.glyph && a.hasRingPart(Prefs.PART_RUNNING))
        val wattsInGlyph = a.glyph && a.placed(if (charging) Prefs.PART_POWER else Prefs.PART_RUNNING)
        val tempInGlyph = a.glyph && a.placed(Prefs.PART_TEMP)
        val showWatts = (a.perfPower || (!a.glyph && (a.hasRingPart(Prefs.PART_POWER) || a.hasRingPart(Prefs.PART_RUNNING)))) &&
            wattsWanted && !wattsInGlyph && !TrioState.sChargePowerW.isNaN()
        if (showWatts) {
            text.append(String.format(Locale.US, "%.1fW", TrioState.sChargePowerW))
        }
        val tempWanted = !a.perfTempChargingOnly || charging
        val showTemp = (a.perfTemp || (!a.glyph && a.hasRingPart(Prefs.PART_TEMP))) &&
            tempWanted && !tempInGlyph &&
            TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN
        if (showTemp) {
            if (text.isNotEmpty()) {
                text.append(PERF_BREAK)
            }
            text.append(TrioState.sBatteryTempC).append("\u00B0C")
        }
        val showCurrent = showCurrentLine(a)
        if (showCurrent) {
            val current = TrioState.currentText(a.currentUnit, a.currentPositive)
            if (!current.isNullOrEmpty()) {
                if (text.isNotEmpty()) {
                    text.append(PERF_BREAK)
                }
                text.append(current)
            }
        }
        if (text.isNotEmpty() && perfStripLines(a) == 1) {
            val fps = stackedFps(a, s)
            if (fps.isNotEmpty()) {
                text.append(PERF_BREAK).append(fps)
            }
        }
        return text.toString()
    }

    /**
     * How many lines the watt and temperature pair occupies right now.
     *
     * Counted from what would actually be drawn - the switches, the readings that exist,
     * and whether the glyph already owns one of them - because "two lines" is what decides
     * whether the frame rate can join the strip.
     */
    private fun perfStripLines(a: TrioAppearance): Int {
        val charging = TrioState.sChargingAttached
        val wattsWanted = charging || a.perfRunning || (!a.glyph && a.hasRingPart(Prefs.PART_RUNNING))
        val wattsInGlyph = a.glyph && a.placed(if (charging) Prefs.PART_POWER else Prefs.PART_RUNNING)
        val showWatts = (a.perfPower || (!a.glyph && (a.hasRingPart(Prefs.PART_POWER) || a.hasRingPart(Prefs.PART_RUNNING)))) &&
            wattsWanted && !wattsInGlyph && !TrioState.sChargePowerW.isNaN()
        var lines = 0
        if (showWatts) lines++
        val tempWanted = !a.perfTempChargingOnly || charging
        val showTemp = (a.perfTemp || (!a.glyph && a.hasRingPart(Prefs.PART_TEMP))) &&
            tempWanted && !(a.glyph && a.placed(Prefs.PART_TEMP)) &&
            TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN
        if (showTemp) lines++
        if (showCurrentLine(a)) lines++
        return lines
    }

    /**
     * Whether the current earns a line of the strip.
     *
     * Its own reading rather than a share with the watt figure: the two answer different
     * questions - what the charger is delivering, and what the battery is taking - and a
     * battery that is not being charged has no watt figure at all while still having a
     * current.
     */
    private fun showCurrentLine(a: TrioAppearance): Boolean =
        (a.perfCurrent || (!a.glyph && a.hasRingPart(Prefs.PART_CURRENT))) &&
            !(a.glyph && a.placed(Prefs.PART_CURRENT)) &&
            TrioState.sCurrentUa != TrioState.CURRENT_UNKNOWN

    /** The frame rate as a line of the strip, or empty when it cannot join it. */
    private fun stackedFps(a: TrioAppearance, s: TrioState): String {
        val fpsWanted = a.perfFps || (!a.glyph && a.hasRingPart(Prefs.PART_FPS))
        if (!fpsWanted || TrioState.sFps <= 0) return ""
        if (a.glyph && a.drawsPart(Prefs.PART_FPS, s.level, s.charging, s.wifiLevel, s.mobileType, s.sims)) {
            return ""
        }
        return TrioState.sFps.toString()
    }

    private fun perfLines(text: String): Int {
        var lines = 1
        for (ch in text) {
            if (ch == PERF_BREAK) lines++
        }
        return lines
    }

    private fun perfLineHeight(size: Float, weight: Int, gapDp: Float = 0f, density: Float = 0f): Float {
        PERF_PROBE.typeface = TrioRenderer.typefaceFor(weight)
        PERF_PROBE.textSize = size
        val metrics = PERF_PROBE.fontMetrics
        return metrics.descent - metrics.ascent + gapDp * density
    }

    /**
     * The text size for the readout, fitted to what the row actually leaves.
     *
     * The user's size multiplier is the *request*, not the answer: past a point the
     * reading is taller than the icon it stands beside (one line) or than both lines
     * together, and drawing it anyway ran it out of the status bar. The requested size
     * is therefore scaled down proportionally until it fits, and never below
     * [PERF_MIN_SIZE_RATIO] of the base so an unmeasured row cannot make it illegible.
     */
    private fun perfTextSize(text: String, row: Row, a: TrioAppearance, density: Float): Float {
        val base = PERF_TEXT_BASE_DP * density
        val lines = perfLines(text).coerceAtLeast(1)
        val requested = base * a.perfFit()
        val gapDp = a.perfLineGap.toFloat()
        val lineHeight = perfLineHeight(requested, a.perfTypeWeight(), gapDp, density)
        if (lineHeight <= 0f) return requested
        val room = row.row.height.toFloat().takeIf { it > 0f } ?: Float.MAX_VALUE
        if (room == Float.MAX_VALUE) return requested
        val needed = lineHeight * lines
        val fitted = if (needed > room) requested * (room / needed) else requested
        return max(base * PERF_MIN_SIZE_RATIO, fitted)
    }

    fun checkCapsuleAvoidance(rowView: ViewGroup) {
        val isCapsule = (Refl.get(sIsCapsuleShownField, rowView) as? Boolean) ?: false
        val isColored = (Refl.get(sIsColoredCapsuleShownField, rowView) as? Boolean) ?: false
        val right = (Refl.get(sCapsuleRightField, rowView) as? Number)?.toInt() ?: 0
        val isShown = (isCapsule || isColored) && right > 0
        val avoidRight = if (isShown) right else 0
        val row = find(rowView) ?: return
        if (row.lastAvoidBound == avoidRight) return
        row.lastAvoidBound = avoidRight
        handleCapsuleAvoidance(rowView, isShown, avoidRight)
    }

    fun handleCapsuleAvoidance(rowView: ViewGroup, isShown: Boolean, capsuleRight: Int) {
        val row = find(rowView) ?: return
        val density = rowView.resources.displayMetrics.density
        val safeGap = (4f * density).toInt()
        val avoidBound = capsuleRight + safeGap
        val loc = IntArray(2)

        val views = listOfNotNull(row.signal, row.type, row.perf, row.fps, row.glyph)
        for (v in views) {
            if (v.parent !== row.container) continue
            if (!isShown || capsuleRight <= 0) {
                v.alpha = 1.0f
                v.translationX = 0f
            } else {
                v.getLocationOnScreen(loc)
                val viewLeftOnScreen = loc[0]
                if (viewLeftOnScreen < avoidBound) {
                    v.alpha = 0f
                    v.translationX = 0f
                } else {
                    v.alpha = 1.0f
                    v.translationX = 0f
                }
            }
        }
    }

    private fun applyBurnInStep(row: Row) {
        val cell = BURN_IN_CELLS[row.burnInIndex % BURN_IN_CELLS.size]
        row.burnInIndex++
        val density = row.row.resources.displayMetrics.density
        val step = 1f * density
        val views = listOfNotNull(row.glyph, row.signal, row.type, row.perf, row.fps)
        val p = row.container
        for (v in views) {
            if (v.parent !== p) continue
            var dx = cell.first * step
            var dy = cell.second * step
            if (p.width > 0 && p.height > 0 && v.width > 0 && v.height > 0) {
                val minX = (p.paddingLeft - v.left).toFloat()
                val maxX = (p.width - p.paddingRight - v.right).toFloat()
                dx = if (minX > maxX) 0f else dx.coerceIn(minX, maxX)
                val minY = (p.paddingTop - v.top).toFloat()
                val maxY = (p.height - p.paddingBottom - v.bottom).toFloat()
                dy = if (minY > maxY) 0f else dy.coerceIn(minY, maxY)
            }
            v.translationX = dx
            v.translationY = dy
        }
    }

    fun resetBurnIn(row: Row) {
        val views = listOfNotNull(row.glyph, row.signal, row.type, row.perf, row.fps)
        for (v in views) {
            if (v.parent === row.container) {
                v.translationX = 0f
                v.translationY = 0f
            }
        }
    }

    fun onScreenStateChanged(screenOn: Boolean) {
        if (!screenOn) {
            for (row in snapshot()) {
                row.row.removeCallbacks(row.burnInTask)
            }
        } else {
            val a = TrioConfig.appearance()
            for (row in snapshot()) {
                if (a.enabled && a.antiBurnIn && row.row.isShown) {
                    row.row.removeCallbacks(row.burnInTask)
                    row.row.postDelayed(row.burnInTask, Prefs.BURN_IN_INTERVAL_MS)
                }
            }
            refreshShown()
            invalidateAll()
        }
    }

    /**
     * Repaints every reading from the live state.
     *
     * Skipped entirely while the bar is hidden: nothing painted into an undrawn row
     * reaches a display, and [TrioState.refresh] behind it polls telephony - a binder
     * round trip per call - which is exactly the work that must not run for a bar
     * nobody is looking at. [TrioHooks.invalidateHosts] records the miss and replays
     * one repaint when the bar comes back, so nothing stays stale.
     */
    fun invalidateAll() {
        if (!BarVisibility.shown) {
            TrioHooks.markRepaintPending()
            return
        }
        for (row in snapshot()) {
            row.state.refresh()
            val fg = row.state.foreground()
            row.glyph?.invalidate()
            row.signal?.invalidate()
            row.perf?.setTextColor(fg)
            row.fps?.setTextColor(fg)
            row.type?.setTextColor(fg)
            row.row.invalidate()
        }
    }

    @SuppressLint("InternalInsetResource")
    private fun barHeight(view: View): Int {
        val res = view.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else view.height
    }

    private fun idView(root: View, name: String): View? {
        val res = root.resources ?: return null
        val id = try {
            res.getIdentifier(name, "id", "com.android.systemui")
        } catch (ignored: Throwable) {
            0
        }
        return if (id != 0) root.findViewById(id) else null
    }

    /** One status-bar icon row and everything the module put in it. */
    internal class Row(
        /** `HwSystemIcons`, the row that owns the whole right-hand group. */
        val row: ViewGroup,
        /** The plain `LinearLayout` that actually holds the icons and the battery. */
        val container: ViewGroup,
        val battery: View,
        val state: TrioState,
    ) {
        var glyph: GlyphView? = null
        var signal: OutSignalView? = null
        var perf: PerfLabel? = null

        /**
         * The frame rate, in a view of its own.
         *
         * It is a reading like the wattage, but it is never stacked into the readouts'
         * two lines: that strip is sized to hold a watt and a temperature, and a third
         * line would shrink all three. A reading that cannot stack gets its own view.
         */
        var fps: PerfLabel? = null
        var type: OutTypeLabel? = null

        /** Last arrangement the glyph was measured for, so a style change re-measures. */
        var lastStyle: Int = -1

        /** True while a full sync is queued for this row. */
        var syncPosted: Boolean = false

        /** The out-of-ring order the views were last arranged in. */
        var outOrderApplied: IntArray? = null

        var lastAvoidBound: Int = -1
        var burnInIndex = 0
        val burnInTask: Runnable = object : Runnable {
            override fun run() {
                if (!ScreenStateTracker.isScreenOn) return
                val a = TrioConfig.appearance()
                if (!a.antiBurnIn) {
                    resetBurnIn(this@Row)
                    return
                }
                // A shift nobody can see is a repaint of five views for nothing; the
                // chain stays alive so the step resumes with the bar.
                if (BarVisibility.shown) applyBurnInStep(this@Row)
                this@Row.row.removeCallbacks(this)
                if (ScreenStateTracker.isScreenOn) {
                    this@Row.row.postDelayed(this, Prefs.BURN_IN_INTERVAL_MS)
                }
            }
        }
    }

    /** The out-of-ring network type label. */
    internal class OutTypeLabel(context: Context) : TextView(context) {
        /** The row state supplying the ink. */
        var host: TrioState? = null

        /**
         * The suffix scale the current text was built with; -1 until first applied.
         * Remembered because a scale change leaves the *characters* identical, so a
         * content comparison alone would skip the rebuild.
         */
        var suffixScale: Int = -1

        init {
            gravity = Gravity.CENTER
            isSingleLine = true
            includeFontPadding = false
            letterSpacing = TrioGeometry.VALUE_LETTER_SPACING
        }
    }

    /** The out-of-ring performance readout: charging power and battery temperature. */
    internal class PerfLabel(context: Context) : TextView(context) {
        /** The row state supplying the ink. */
        var host: TrioState? = null

        init {
            gravity = Gravity.CENTER
            includeFontPadding = false
            letterSpacing = TrioGeometry.VALUE_LETTER_SPACING
        }
    }

    /**
     * The out-of-ring SIM reading: four/five ascending capsule bars, and the dot row beneath
     * them when both SIMs have a reading.
     *
     * A view of our own rather than Honor's signal icon: that icon is one icon for one
     * reading, while this mode draws a reading per SIM and has to place them itself.
     */
    internal class OutSignalView(context: Context) : View(context) {
        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        var host: TrioState? = null

        /** Bars to light. */
        var bars: Int = 0

        /** Dots to light, or `-1` when there is no second reading. */
        var dots: Int = -1

        /** Each card's network type, when the labels are switched on. */
        var type: String? = null
        var type2: String? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val a = TrioConfig.appearance()
            val dual = dots >= 0
            val density = resources.displayMetrics.density
            val lp = layoutParams
            val h = if (lp != null && lp.height > 0) {
                lp.height
            } else {
                val specH = MeasureSpec.getSize(heightMeasureSpec)
                if (specH > 0) specH else TrioRenderer.outSignalHeight(a, a.outSignalSize, density, dual)
            }
            val w = if (lp != null && lp.width > 0) {
                lp.width
            } else {
                TrioRenderer.outSignalWidth(h, a, type, type2, dual)
            }
            setMeasuredDimension(
                resolveSize(w, widthMeasureSpec),
                resolveSize(h, heightMeasureSpec),
            )
        }
        override fun onDraw(canvas: Canvas) {
            val source = host ?: return
            TrioRenderer.drawOutSignal(
                canvas, width, height, bars, dots,
                source.foreground(), TrioConfig.appearance(), type, type2,
            )
        }
    }
}
