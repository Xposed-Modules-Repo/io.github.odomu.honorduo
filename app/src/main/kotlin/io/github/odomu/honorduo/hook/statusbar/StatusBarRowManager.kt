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

package io.github.odomu.honorduo.hook.statusbar

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Paint
import android.os.Build
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
import androidx.annotation.RequiresApi
import io.github.odomu.honorduo.hook.core.Refl
import io.github.odomu.honorduo.hook.core.StatusHooks
import io.github.odomu.honorduo.hook.statusbar.StatusBarRowManager.PERF_MIN_SIZE_RATIO
import io.github.odomu.honorduo.hook.tracker.BarVisibility
import io.github.odomu.honorduo.hook.tracker.ScreenStateTracker
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconGeometry
import io.github.odomu.honorduo.ui.render.IconRenderer
import io.github.odomu.honorduo.ui.view.GlyphView
import io.github.odomu.honorduo.ui.view.OutSignalView
import io.github.odomu.honorduo.ui.view.OutTypeLabel
import io.github.odomu.honorduo.ui.view.PerfLabel
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun register(row: ViewGroup) {
        if (find(row) != null) return
        val battery = idView(row, "battery") ?: return
        val parent = battery.parent as? ViewGroup ?: return
        val created = Row(row, parent, battery, StatusState(battery))
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
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

    fun syncAll(a: StatusAppearance = ModuleConfig.appearance(), force: Boolean = true) {
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
        val a = ModuleConfig.appearance()
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
            StatusHooks.markRepaintPending()
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
        val a = ModuleConfig.appearance()
        for (slot in a.outOrder) {
            when (slot) {
                Prefs.OUT_SLOT_SIGNAL -> syncSignal(row, a)
                Prefs.OUT_SLOT_READOUTS -> syncPerfReadouts(row, a)
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
    private fun reorderOutViews(row: Row, a: StatusAppearance) {
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
    private fun syncSignal(row: Row, a: StatusAppearance) {
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
        IconRenderer.outSignalLevels(
            a.dataSimOnly, row.state.mobileLevel,
            row.state.slotLevels, StatusState.sDataSlot, row.state.sims, reading,
        )
        // Each card's icon name states its level, even when telephony cannot answer.
        val native = nativeSlotLevels(row.container)
        if (native != null && native.size >= 2) {
            // "Data SIM only" still means one row: reading the cards must not quietly
            // undo it by putting the second card back.
            if (a.dataSimOnly) {
                val slot = StatusState.sDataSlot
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
        val fallback = StatusState.sMobileType.ifEmpty { null }
        val dataSlot = StatusState.sDataSlot.coerceIn(0, row.state.slotTypes.size - 1)
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
        var height = IconRenderer.outSignalHeight(a, a.outSignalSize, density, dual)
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
        val width = IconRenderer.outSignalWidth(
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
        val count = minOf(group.childCount, StatusState.SIM_SLOTS)
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

    private class ActivePerfItem(
        val id: Int,
        val text: String,
        val isTemp: Boolean = false,
        val isHighTemp: Boolean = false,
    )

    /**
     * Synchronises the out-of-ring performance readouts (Power, Temp, Current, FPS).
     *
     * Items are ordered by [StatusAppearance.perfOrder] (reflecting the sequence in which
     * they were enabled). At most two items stack in a single label:
     * - 1 item: single readout in group 1.
     * - 2 items: 2 stacked in group 1.
     * - 3 items: 2 stacked in group 1, 3rd alone in group 2.
     * - 4 items: 2 stacked in group 1, 2 stacked in group 2.
     */
    private fun syncPerfReadouts(row: Row, a: StatusAppearance) {
        if (!a.perfPairShown()) {
            remove(row, row.perf)
            remove(row, row.fps)
            row.perf = null
            row.fps = null
            return
        }
        row.state.refresh()
        val density = row.row.resources.displayMetrics.density

        val charging = StatusState.sChargingAttached
        val wattsWanted = charging || a.perfRunning || (!a.glyph && a.hasRingPart(Prefs.PART_RUNNING))
        val wattsInGlyph = a.glyph && a.placed(if (charging) Prefs.PART_POWER else Prefs.PART_RUNNING)
        val showWatts = (a.perfPower || (!a.glyph && (a.hasRingPart(Prefs.PART_POWER) || a.hasRingPart(Prefs.PART_RUNNING)))) &&
            wattsWanted && !wattsInGlyph && !StatusState.sChargePowerW.isNaN()
        val powerItem = if (showWatts) {
            ActivePerfItem(Prefs.PERF_ITEM_POWER, String.format(Locale.US, "%.1fW", StatusState.sChargePowerW))
        } else null

        val tempWanted = !a.perfTempChargingOnly || charging
        val tempInGlyph = a.glyph && a.placed(Prefs.PART_TEMP)
        val showTemp = (a.perfTemp || (!a.glyph && a.hasRingPart(Prefs.PART_TEMP))) &&
            tempWanted && !tempInGlyph &&
            StatusState.sBatteryTempC != StatusState.TEMP_UNKNOWN
        val tempItem = if (showTemp) {
            ActivePerfItem(
                Prefs.PERF_ITEM_TEMP,
                "${StatusState.sBatteryTempC}\u00B0C",
                isTemp = true,
                isHighTemp = a.roleColorsTemp && StatusState.sBatteryHot,
            )
        } else null

        val currentInGlyph = a.glyph && a.placed(Prefs.PART_CURRENT)
        val showCurrent = (a.perfCurrent || (!a.glyph && a.hasRingPart(Prefs.PART_CURRENT))) &&
            !currentInGlyph && StatusState.sCurrentUa != StatusState.CURRENT_UNKNOWN
        val currentText = if (showCurrent) StatusState.currentText(a.currentUnit, a.currentPositive) else null
        val currentItem = if (!currentText.isNullOrEmpty()) {
            ActivePerfItem(Prefs.PERF_ITEM_CURRENT, currentText)
        } else null

        val fpsWanted = a.perfFps || (!a.glyph && a.hasRingPart(Prefs.PART_FPS))
        val fpsInGlyph = a.glyph && a.drawsPart(Prefs.PART_FPS, row.state.level, row.state.charging, row.state.wifiLevel, row.state.mobileType, row.state.sims)
        val showFps = fpsWanted && !fpsInGlyph && StatusState.sFps > 0
        val fpsItem = if (showFps) {
            ActivePerfItem(Prefs.PERF_ITEM_FPS, StatusState.sFps.toString())
        } else null

        val activeList = ArrayList<ActivePerfItem>(4)
        for (id in a.perfOrder) {
            when (id) {
                Prefs.PERF_ITEM_POWER -> powerItem?.let { activeList.add(it) }
                Prefs.PERF_ITEM_TEMP -> tempItem?.let { activeList.add(it) }
                Prefs.PERF_ITEM_CURRENT -> currentItem?.let { activeList.add(it) }
                Prefs.PERF_ITEM_FPS -> fpsItem?.let { activeList.add(it) }
            }
        }

        val group1 = activeList.take(2)
        val group2 = if (activeList.size > 2) activeList.drop(2).take(2) else emptyList()

        row.perf = applyReadoutGroup(row, a, row.perf, group1, density)
        row.fps = applyReadoutGroup(row, a, row.fps, group2, density)
    }

    private fun applyReadoutGroup(
        row: Row,
        a: StatusAppearance,
        existing: PerfLabel?,
        items: List<ActivePerfItem>,
        density: Float,
    ): PerfLabel? {
        if (items.isEmpty()) {
            remove(row, existing)
            return null
        }
        var label = existing
        if (label == null) {
            label = PerfLabel(row.row.context)
            insertBefore(row, label)
            row.outOrderApplied = null
        }
        label.host = row.state
        val text = items.joinToString(PERF_BREAK.toString()) { it.text }
        applyTypeface(label, a.perfTypeWeight())
        val targetSize = perfTextSize(text, row, a, density)
        if (kotlin.math.abs(label.textSize - targetSize) > 0.05f) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetSize)
        }
        label.setLineSpacing(a.perfLineGap * density, 1f)

        val baseColor = row.state.foreground()
        val highTempItem = items.firstOrNull { it.isHighTemp }
        if (label.text.toString() != text) {
            if (highTempItem != null) {
                val highColor = if (IconRenderer.isDarkBackground(baseColor)) a.highTempOnDark else a.highTempOnLight
                val tempStr = highTempItem.text
                val idx = text.indexOf(tempStr)
                if (idx >= 0) {
                    val span = SpannableStringBuilder(text)
                    span.setSpan(
                        ForegroundColorSpan(highColor),
                        idx, idx + tempStr.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    label.text = span
                } else {
                    label.text = text
                }
            } else {
                label.text = text
                label.setTextColor(baseColor)
            }
        }
        size(label, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, a.outTypeMarginRight, 0, density)
        StatusBarIconManager.release(label)
        return label
    }
    /** The network type, when it is drawn outside the glyph. */
    private fun syncType(row: Row, a: StatusAppearance) {
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
        val endMargin = (gapEndDp * density).roundToInt()
        val startMargin = (gapStartDp * density).roundToInt()
        if (lp.width == width && lp.height == height &&
            lp.marginEnd == endMargin && lp.marginStart == startMargin &&
            lp.gravity == Gravity.CENTER_VERTICAL
        ) {
            return
        }
        lp.width = width
        lp.height = height
        lp.gravity = Gravity.CENTER_VERTICAL
        lp.marginEnd = endMargin
        lp.marginStart = startMargin
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
        val face = IconRenderer.typefaceFor(weight)
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
        if (!IconGeometry.hasShrunkSuffix(text, scalePercent)) {
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

    private fun perfLines(text: String): Int {
        var lines = 1
        for (ch in text) {
            if (ch == PERF_BREAK) lines++
        }
        return lines
    }

    private fun perfLineHeight(size: Float, weight: Int, gapDp: Float = 0f, density: Float = 0f): Float {
        PERF_PROBE.typeface = IconRenderer.typefaceFor(weight)
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
    private fun perfTextSize(text: String, row: Row, a: StatusAppearance, density: Float): Float {
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
            val a = ModuleConfig.appearance()
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
     * reaches a display, and [StatusState.refresh] behind it polls telephony - a binder
     * round trip per call - which is exactly the work that must not run for a bar
     * nobody is looking at. [StatusHooks.invalidateHosts] records the miss and replays
     * one repaint when the bar comes back, so nothing stays stale.
     */
    fun invalidateAll() {
        if (!BarVisibility.shown) {
            StatusHooks.markRepaintPending()
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
        val id = Refl.resId(res, "status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else view.height
    }

    private fun idView(root: View, name: String): View? {
        val res = root.resources ?: return null
        val id = Refl.resId(res, name)
        return if (id != 0) root.findViewById(id) else null
    }

    /** One status-bar icon row and everything the module put in it. */
    internal class Row(
        /** `HwSystemIcons`, the row that owns the whole right-hand group. */
        val row: ViewGroup,
        /** The plain `LinearLayout` that actually holds the icons and the battery. */
        val container: ViewGroup,
        val battery: View,
        val state: StatusState,
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
                val a = ModuleConfig.appearance()
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
}
