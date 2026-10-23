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
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isGone
import io.github.odomu.honorduo.hook.TrioHooks.PERF_MIN_SIZE_RATIO
import io.github.odomu.honorduo.hook.TrioHooks.allowOverflow
import io.github.odomu.honorduo.hook.TrioHooks.owned
import io.github.odomu.honorduo.hook.TrioHooks.syncRow
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioState
import io.github.odomu.honorduo.render.GlyphView
import io.github.odomu.honorduo.render.TrioGeometry
import io.github.odomu.honorduo.render.TrioRenderer
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Calendar
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the module installs into MagicOS' SystemUI.
 *
 * ### Where the glyph lives
 * The status bar's right-hand row on MagicOS is `HwSystemIcons`, a plain horizontal
 * `LinearLayout` whose children are, in order, the notification icons, the
 * network-speed slots, `signalIcons` (the Wi-Fi + mobile cluster from
 * `hw_system_icons.xml`) and `battery`. The module inserts one child of its own
 * immediately before the battery and hides the battery, so the row lays the glyph out
 * exactly where the battery icon used to stand and reserves its width for it.
 *
 * This is deliberately a real child view rather than a drawable handed to the
 * battery: on MagicOS the battery is `HwStatusBarBatteryView`, a `LinearLayout` that
 * paints a border bitmap plus level, bolt, save and two percentage views - there is
 * no single drawable slot to take over, and the battery's width is pinned by its own
 * artwork. A child of the row owns its width, which is what lets the capsule be as
 * wide as it is, and its canvas is still the platform's, so nothing is read as an
 * overlay window.
 *
 * ### Why the native icons are hidden at their own setters
 * Honor re-shows its Wi-Fi and mobile groups from several places - the binders, the
 * cluster's callbacks, the airplane path. Each of those ends in
 * `SignalClusterView.apply$1` (callbacks) or a layout pass, so the suppression is
 * re-applied from both and never depends on winning a race.
 */
internal object TrioHooks {

    const val TAG = "HonorDuo"

    const val LOG_INFO = 4
    const val LOG_WARN = 5
    const val LOG_ERROR = 6

    /** Forces the view-tree dumps used to pin down a container. */
    const val DEBUG_DUMP = false

    /** Module handle for logging from helpers that have no callback argument. */
    @Volatile
    private var sModule: XposedModule? = null

    /** Every row this module has mounted a glyph into. */
    private val ROWS = ArrayList<Row>()

    /** One status-bar icon row and everything the module put in it. */
    private class Row(
        /** `HwSystemIcons`, the row that owns the whole right-hand group. */
        @JvmField val row: ViewGroup,
        /** The plain `LinearLayout` that actually holds the icons and the battery. */
        @JvmField val container: ViewGroup,
        @JvmField val battery: View,
        @JvmField val state: TrioState,
    ) {
        @JvmField var glyph: GlyphView? = null
        @JvmField var signal: OutSignalView? = null
        @JvmField var perf: PerfLabel? = null

        /**
         * The frame rate, in a view of its own.
         *
         * It is a reading like the wattage, but it is never stacked into the readouts'
         * two lines: that strip is sized to hold a watt and a temperature, and a third
         * line would shrink all three. A reading that cannot stack gets its own view.
         */
        @JvmField var fps: PerfLabel? = null
        @JvmField var type: OutTypeLabel? = null

        /** Last arrangement the glyph was measured for, so a style change re-measures. */
        @JvmField var lastStyle: Int = -1

        /** True while a full sync is queued for this row. */
        @JvmField var syncPosted: Boolean = false

        /** The out-of-ring order the views were last arranged in. */
        @JvmField var outOrderApplied: IntArray? = null

        var burnInIndex = 0
        val burnInTask: Runnable = object : Runnable {
            override fun run() {
                val a = TrioConfig.appearance()
                if (!a.antiBurnIn) {
                    resetBurnIn(this@Row)
                    return
                }
                applyBurnInStep(this@Row)
                this@Row.row.removeCallbacks(this)
                this@Row.row.postDelayed(this, Prefs.BURN_IN_INTERVAL_MS)
            }
        }
    }

    @Volatile
    private var sUnitTypeField: Field? = null

    /** Cached `R.id.battery` / `R.id.signalIcons` lookups. */
    private val IDS = HashMap<String, Int>()

    fun install(module: XposedModule, cl: ClassLoader) {
        sModule = module
        // Read the user's settings before anything is hooked: the very first hook
        // callback already needs to know whether the module is switched on.
        TrioConfig.install(module)
        TrioConfig.addListener { applyConfigChange() }
        var hooked = 0
        hooked += hookRow(module, cl)
        hooked += hookBattery(module, cl)
        hooked += hookVisibility(module)
        hooked += hookSignalIcons(module, cl)
        hooked += hookTypeViews(module, cl)
        hooked += hookContents(module, cl)
        hooked += hookClockFace(module, cl)
        hooked += hookTint(module, cl)
        hooked += hookPivotXY(module, cl)
        log(
            module,
            "HonorDuo installed, hooks=$hooked enabled=${TrioConfig.get().enabled}",
        )
    }

    /**
     * Reacts to the settings app changing a value.
     *
     * Runs on whatever thread delivered the change - the framework's Binder thread, or
     * the main looper for the settings app's reload broadcast - so everything that
     * touches the view tree is posted to the main looper.
     */
    private fun applyConfigChange() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { applyConfigChange() }
            return
        }
        val a = TrioConfig.appearance()
        if (debugLog()) {
            log(
                LOG_INFO,
                "config changed, rows=${rows()} enabled=${a.glyph} style=${a.style}",
            )
        }
        for (row in snapshot()) {
            row.row.post {
                syncRow(row, true)
                row.row.requestLayout()
                row.row.invalidate()
            }
        }
        syncBarClock()
        sBarClockView?.let { view ->
            val native = sBarClock?.get() ?: (sContents?.get()?.findViewById(sClockId) as? TextView)
            if (native != null) {
                updateBarClock(view, a, native)
            }
            view.invalidate()
        }
        applyIconVisibility()
    }

    /**
     * Mounts a glyph into every `HwSystemIcons` row and keeps it in step with that
     * row's layout.
     */
    private fun hookRow(module: XposedModule, cl: ClassLoader): Int {
        val rowClass = Refl.cls("com.android.systemui.statusbar.HwSystemIcons", cl)
        if (rowClass == null) {
            log(module, "HwSystemIcons missing")
            return 0
        }
        var n = 0
        n += hook(
            module, Refl.method(rowClass, "onFinishInflate"),
            "honorduo-row-inflate",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                register(self)
            }
            result
        }
        n += hook(
            module, Refl.method(
                rowClass, "onLayout",
                Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ),
            "honorduo-row-layout",
            XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val self = chain.thisObject
                if (self is ViewGroup) {
                    val row = find(self)
                    if (row != null) {
                        // Layout is where Honor re-shows its battery, and where a row that lost its glyph gets it back.
                        requestSync(row)
                    }
                    checkCapsuleAvoidanceAndRescue(self)
                }
                result
            },
        )
        sCapsuleRightField = Refl.field(rowClass, "mCapsuleRight")
        sIsCapsuleShownField = Refl.field(rowClass, "mIsCapsuleShown")
        sIsColoredCapsuleShownField = Refl.field(rowClass, "mIsColoredCapsuleShown")
        n += hook(
            module, methodNamed(rowClass, "updateIconsAlpha", 0),
            "honorduo-update-icons-alpha",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                checkCapsuleAvoidanceAndRescue(self)
            }
            result
        }
        n += hook(
            module, methodNamed(rowClass, "setCapsuleArea", 5),
            "honorduo-capsule-area",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                val isShown = chain.getArg(1) as? Boolean ?: false
                val right = (chain.getArg(2) as? Number)?.toInt() ?: 0
                handleCapsuleAvoidanceAndRescue(self, isShown, right)
            }
            result
        }
        n += hook(
            module, methodNamed(rowClass, "setColoredCapsuleArea", 3),
            "honorduo-colored-capsule-area",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                val right = (chain.getArg(1) as? Number)?.toInt() ?: 0
                val isShown = chain.getArg(2) as? Boolean ?: false
                handleCapsuleAvoidanceAndRescue(self, isShown, right)
            }
            result
        }
        n += hook(
            module, Refl.method(rowClass, "onAttachedToWindow"),
            "honorduo-row-attach",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                register(self)
            }
            result
        }
        n += hook(
            module, Refl.method(rowClass, "onDetachedFromWindow"),
            "honorduo-row-detach",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                drop(self)
            }
            result
        }
        return n
    }

    /** Registers (or refreshes) the row for a `HwSystemIcons` instance. */
    private fun register(row: ViewGroup) {
        if (find(row) != null) {
            return
        }
        val battery = idView(row, "battery") ?: return
        val parent = battery.parent as? ViewGroup ?: return
        // The battery's own parent is the `system_icons` LinearLayout that also holds the notification icons and the signal cluster.
        val created = Row(row, parent, battery, TrioState(battery))
        synchronized(ROWS) { ROWS.add(created) }
        row.post { syncRow(created, true) }
    }

    private fun drop(row: ViewGroup) {
        synchronized(ROWS) {
            for (i in ROWS.indices.reversed()) {
                if (ROWS[i].row === row) {
                    val r = ROWS.removeAt(i)
                    r.row.removeCallbacks(r.burnInTask)
                    resetBurnIn(r)
                }
            }
        }
    }

    private fun find(row: ViewGroup): Row? {
        synchronized(ROWS) {
            for (r in ROWS) {
                if (r.row === row) {
                    return r
                }
            }
        }
        return null
    }

    private fun snapshot(): List<Row> = synchronized(ROWS) { ArrayList(ROWS) }

    private fun rows(): Int = synchronized(ROWS) { ROWS.size }

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
    private fun syncRow(row: Row, force: Boolean) {
        val a = TrioConfig.appearance()
        // Resolved here so the first mount already knows which children to take over.
        resolveIds(row.row.resources)
        if (!a.glyph) {
            unmountGlyph(row)
            unmountOutViews(row)
            // The battery is the system's own view; the module only borrowed its slot.
            release(row.battery)
            return
        }
        mountGlyph(row)
        // GONE, not INVISIBLE: the row has to close up over the battery, and its own
        // measurement adds up the children's widths.
        hide(row.battery)
        settle(row.container, a)
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
    private fun requestSync(row: Row) {
        synchronized(row) {
            if (row.syncPosted) {
                return
            }
            row.syncPosted = true
        }
        row.row.post {
            synchronized(row) { row.syncPosted = false }
            syncRow(row, false)
        }
    }

    private fun mountGlyph(row: Row) {
        if (row.glyph != null) {
            return
        }
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
        if (!a.glyph) {
            return
        }
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
        if (applied != null && applied.contentEquals(order)) {
            return
        }
        row.outOrderApplied = order.copyOf()
        // The stored order runs nearest-first, so it is walked backwards to insert in order.
        for (slot in order.reversed()) {
            val view = when (slot) {
                Prefs.OUT_SLOT_SIGNAL -> row.signal
                Prefs.OUT_SLOT_READOUTS -> row.perf
                Prefs.OUT_SLOT_TYPE -> row.type
                else -> null
            } ?: continue
            if (view.parent !== row.container) {
                continue
            }
            // The frame rate rides with the readouts: it is a reading, not an order of
            // its own, and a fourth slot would ask the user to rank a number against the
            // strip it belongs to.
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
        // Alone: one label on the right, and it names the card that carries the data.
        // "Alone" puts the type in the separate label, so the reading carries none.
        val alone = a.stackLabel == Prefs.STACK_LABEL_ALONE
        // The rows are drawn in slot order, so each label is that row's own card. Using
        // the data card's type for the first row put "5G" over the 4G card whenever the
        // data SIM sat in the second slot, which is what a two-row reading kept showing.
        val dataSlot = TrioState.sDataSlot.coerceIn(0, row.state.slotTypes.size - 1)
        val slots = row.state.slotLevels
        val dual = reading[1] >= 0
        val firstSlot = when {
            a.dataSimOnly -> dataSlot
            dual -> 0
            // A lone card is drawn in the first row whichever slot it is in.
            slots[0] >= 0 -> 0
            else -> dataSlot
        }
        signal.type = row.state.slotTypes[firstSlot].ifEmpty { fallback }
        // For the second card, use slot 1's type; fall back to the sampled type so no row goes blank.
        signal.type2 = if (a.dataSimOnly || !dual) {
            null
        } else {
            row.state.slotTypes[1].ifEmpty { fallback }
        }
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
        val marginDp = if (a.outSignalMargin in 0..16) a.outSignalMargin else OUT_VIEW_MARGIN_DP
        size(signal, width, height, marginDp, 0, density)
        signal.translationY = 0f
        show(signal)
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
        if (!sIdsResolved || sMobileGroupId == 0) {
            return null
        }
        val group = root.findViewById<ViewGroup>(sMobileGroupId)
            ?: sContents?.get()?.findViewById(sMobileGroupId)
            ?: return null
        val count = minOf(group.childCount, TrioState.SIM_SLOTS)
        if (count < 2) {
            return null
        }
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
        val icon = if (sMobileSignalId != 0) card.findViewById<View>(sMobileSignalId) else null
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
        if (iconId <= 0) {
            return null
        }
        val name = runCatching {
            card.resources.getResourceEntryName(iconId)
        }.getOrNull() ?: return null
        if (name.contains("signal_null")) {
            return 0
        }
        // Five-step icon sets, and the four-step ones scaled onto the same 0..5, then
        // onto the four dots the reading draws.
        val five = Regex("(?:five|5bars|5)_(\\d)").find(name)
        val raw = if (five != null) {
            five.groupValues[1].toIntOrNull()
        } else {
            Regex("signal.*_(\\d)").find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { (it * 5 + 2) / 4 }
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
        val tempIsHigh = TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN && TrioState.sBatteryTempC >= a.tempThreshold
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
        size(
            perf, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            a.outTypeMarginRight, 0, density,
        )
        if (text.isEmpty()) {
            hide(perf)
        } else {
            show(perf)
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
        size(
            label, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            a.outTypeMarginRight, a.outTypeMarginLeft, density,
        )
        show(label)
    }

    /**
     * The frame rate as its own view draws it: "120", or empty.
     *
     * Empty in two cases: the glyph is drawing the piece itself, or the strip had a free
     * second line and took it - one frame rate, drawn once. It is mounted only when the
     * strip is already full (or empty), which is the case a third line would break.
     */
    private fun fpsText(a: TrioAppearance, s: TrioState): String {
        if (!a.perfFps || TrioState.sFps <= 0) {
            return ""
        }
        if (a.drawsPart(
                Prefs.PART_FPS, s.level, s.charging, s.wifiLevel, s.mobileType, s.sims,
            )
        ) {
            return ""
        }
        // A free line in the strip is the better home: same size, same block.
        if (perfStripLines(a) == 1) {
            return ""
        }
        return TrioState.sFps.toString()
    }

    /** The network type, when it is drawn outside the glyph. */
    private fun syncType(row: Row, a: TrioAppearance) {
        // The stacked reading draws the type itself, so the standalone label is not mounted.
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
            // A new view lands before the glyph; forget the order so it is re-applied.
            row.outOrderApplied = null
        }
        label.host = row.state
        row.state.refresh()
        val text = row.state.mobileType
        if (text.isEmpty()) {
            hide(label)
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
        show(label)
    }

    /** Inserts [view] immediately before the glyph, so the row orders them. */
    private fun insertBefore(row: Row, view: View) {
        val anchor: View = row.glyph ?: row.battery
        val at = row.container.indexOfChild(anchor)
        row.container.addView(
            view, if (at < 0) row.container.childCount else at,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /**
     * Places one out-of-ring view in the row.
     *
     * The gaps are given in dp and converted here, so the three call sites cannot round
     * them differently. A row adds both sides of a neighbouring pair, which is why every
     * view here sets an end margin and leaves the start at zero except the type label.
     */
    private fun size(
        view: View, width: Int, height: Int,
        gapEndDp: Int, gapStartDp: Int, density: Float,
    ) {
        val lp = view.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(width, height)
        lp.width = width
        lp.height = height
        lp.gravity = Gravity.CENTER_VERTICAL
        lp.marginEnd = (gapEndDp * density).roundToInt()
        lp.marginStart = (gapStartDp * density).roundToInt()
        view.layoutParams = lp
    }

    private fun remove(row: Row, view: View?) {
        if (view == null) {
            return
        }
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
        row.signal = null
        row.perf = null
        row.type = null
        // The views are gone, so the order has to be re-applied when they come back:
        // otherwise the next mount keeps the list order it was created in.
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
                RelativeSizeSpan(scalePercent / 100f), at, text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            label.text = span
        }
        label.suffixScale = scalePercent
    }

    /** Fixed outer gap of the out-of-ring reading, in dp. */
    private const val OUT_VIEW_MARGIN_DP = 2

    /** Delay past the minute boundary, so the ROM's own update lands first. */
    private const val BAR_CLOCK_MINUTE_LAG_MS = 250L

    /** Share of the screen width the date line may use. */
    private const val BAR_CLOCK_WIDTH_SHARE = 0.42f

    /** How many ancestors of the clock are told not to clip. */
    private const val BAR_CLOCK_CLIP_DEPTH = 2

    /** Gap between the date line and the time, as a share of the time's size. */
    private const val BAR_CLOCK_GAP = 0.08f

    /** Line-spacing multiplier for the two lines, tighter than the default leading. */
    private const val BAR_CLOCK_LINE_SPACING = 0.86f

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

    private fun perfText(a: TrioAppearance, s: TrioState): String {
        if (!a.perfAnywhere()) {
            return ""
        }
        val text = StringBuilder()
        val charging = TrioState.sChargingAttached
        val wattsWanted = charging || a.perfRunning
        // A watt figure placed in the glyph owns its value; the strip must not repeat it.
        val wattsInGlyph = a.placed(if (charging) Prefs.PART_POWER else Prefs.PART_RUNNING)
        val tempInGlyph = a.placed(Prefs.PART_TEMP)
        if (a.perfPower && wattsWanted && !wattsInGlyph && !TrioState.sChargePowerW.isNaN()) {
            text.append(String.format(Locale.US, "%.1fW", TrioState.sChargePowerW))
        }
        val tempWanted = !a.perfTempChargingOnly || charging
        if (a.perfTemp && tempWanted && !tempInGlyph &&
            TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN
        ) {
            if (text.isNotEmpty()) {
                text.append(PERF_BREAK)
            }
            text.append(TrioState.sBatteryTempC).append("\u00B0C")
        }
        // The frame rate takes the strip's second line while that line is free. With both
        // a watt and a temperature already in it the pair is full, and a third line would
        // shrink all three - so there it is drawn in a view of its own instead.
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
        val wattsWanted = charging || a.perfRunning
        val wattsInGlyph = a.placed(if (charging) Prefs.PART_POWER else Prefs.PART_RUNNING)
        var lines = 0
        if (a.perfPower && wattsWanted && !wattsInGlyph && !TrioState.sChargePowerW.isNaN()) {
            lines++
        }
        if (a.perfTemp && (!a.perfTempChargingOnly || charging) &&
            !a.placed(Prefs.PART_TEMP) &&
            TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN
        ) {
            lines++
        }
        return lines
    }

    /** The frame rate as a line of the strip, or empty when it cannot join it. */
    private fun stackedFps(a: TrioAppearance, s: TrioState): String {
        if (!a.perfFps || TrioState.sFps <= 0) return ""
        // The placement question is answered from the frame's own readings, so the two
        // places that can draw a frame rate never both decide it is theirs.
        if (a.drawsPart(
                Prefs.PART_FPS, s.level, s.charging, s.wifiLevel, s.mobileType, s.sims,
            )
        ) {
            return ""
        }
        return TrioState.sFps.toString()
    }

    private fun perfLines(text: String): Int {
        var lines = 1
        for (ch in text) {
            if (ch == PERF_BREAK) {
                lines++
            }
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
        // Its own base, not the out-of-ring type's size: that slider used to move the
        // watt and temperature text as well, so the reading's own row did nothing on its
        // own. Only `perfScale` sizes this strip now.
        val base = PERF_TEXT_BASE_DP * density
        val lines = perfLines(text).coerceAtLeast(1)
        val requested = base * a.perfFit()
        val gapDp = a.perfLineGap.toFloat()
        val lineHeight = perfLineHeight(requested, a.perfTypeWeight(), gapDp, density)
        if (lineHeight <= 0f) {
            return requested
        }
        // The row's height is the bound. The glyph's ink height used to bound it as well,
        // which made the size slider a no-op: that bound sat below the base size, so every
        // value the slider offers was scaled back to the same number.
        val room = row.row.height.toFloat().takeIf { it > 0f } ?: Float.MAX_VALUE
        if (room == Float.MAX_VALUE) {
            return requested
        }
        val needed = lineHeight * lines
        val fitted = if (needed > room) requested * (room / needed) else requested
        return max(base * PERF_MIN_SIZE_RATIO, fitted)
    }

    /**
     * Re-applies the suppression after every battery update.
     *
     * `HwStatusBarBatteryView.onBatteryUpdate` ends in `setVisibility(0)`, so the
     * battery comes back on its own schedule - every level change, every plug event.
     * Hiding it here, after the update has run, is what makes the suppression survive.
     */
    private fun hookBattery(module: XposedModule, cl: ClassLoader): Int {
        val battery = Refl.cls("com.android.systemui.battery.HwStatusBarBatteryView", cl)
        if (battery == null) {
            log(module, "HwStatusBarBatteryView missing")
            return 0
        }
        var n = 0
        n += hook(
            module, Refl.method(battery, "onBatteryUpdate"),
            "honorduo-battery-update",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                if (TrioConfig.appearance().glyph) {
                    hide(self)
                }
                val row = rowOfBattery(self)
                if (row != null) {
                    syncRow(row, false)
                }
                invalidateHosts()
            }
            result
        }
        n += hook(
            module, Refl.method(battery, "onFinishInflate"),
            "honorduo-battery-inflate",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                // First real context the module gets: it is the one the samplers need, and the row that holds this battery has already been inflated.
                TrioState.attachContext(self.context)
                TrioConfig.installReceiver(self.context)
            }
            result
        }
        return n
    }

    private fun rowOfBattery(battery: View): Row? {
        synchronized(ROWS) {
            for (r in ROWS) {
                if (r.battery === battery) {
                    return r
                }
            }
        }
        return null
    }

    /**
     * Hides Honor's own Wi-Fi and mobile indicators while the glyph carries them, and
     * keeps the battery out of the row.
     *
     * Three views have to stay hidden, and every one of them is re-shown by Honor on
     * its own schedule:
     *
     * - `wifi_combo` - set VISIBLE by the Wi-Fi binder on every connection change;
     * - `mobile_combo` - set VISIBLE by `HwSignalClusterView.applyHonor`, which every
     *   cluster callback ends in;
     * - the battery itself - `HwStatusBarBatteryView.onBatteryUpdate` ends in
     *   `setVisibility(0)`.
     *
     * Rather than chase each of those call sites, the suppression is enforced at the
     * one method they all go through: [View.setVisibility]. The check is an id
     * comparison against cached resource ids, so the cost on a hot framework method is
     * a single field read and an `int` compare. The recursion is bounded - the forced
     * call re-enters the hook, sees the view is already GONE and returns.
     *
     * Ownership is asked per view, not per switch: hiding a group whose reading the
     * glyph does *not* draw would delete an indicator from the bar, so each id is
     * gated on the appearance rule that owns it.
     */
    private fun hookVisibility(module: XposedModule): Int =
        hook(
            module, Refl.method(View::class.java, "setVisibility", Int::class.javaPrimitiveType!!),
            "honorduo-visibility",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                // Recorded before the module can overwrite it: this call IS the
                // system's own decision, and a later hand-back has to restore it.
                if (!writingSuppressed()) noteRequested(self)
                enforceSuppression(self)
            }
            result
        }

    /** Cached ids of the views the module suppresses; 0 until resolved. */
    @Volatile
    private var sWifiId = 0

    @Volatile
    private var sMobileId = 0

    @Volatile
    private var sBatteryId = 0

    @Volatile
    private var sClockId = 0

    /** The native signal group and the icon inside each card. */
    @Volatile
    private var sMobileGroupId = 0

    @Volatile
    private var sMobileSignalId = 0

    @Volatile
    private var sIdsResolved = false

    /**
     * The one ownership rule: which status-bar icon this module replaces right now.
     *
     * The visibility hook, the settings change and mounting a row all ask this and
     * nothing else, so hiding an icon and handing it back cannot disagree.
     */
    private fun owned(id: Int, a: TrioAppearance): Boolean = when {
        !a.glyph -> false
        id == sWifiId -> a.ownsWifi()
        id == sMobileId -> a.foldsMobile()
        id == sBatteryId -> true
        // Owned when the glyph draws the time, or when the module's own clock stands in.
        id == sClockId -> a.hidesStatusClock() ||
            sBarClockView?.isAttachedToWindow == true
        else -> false
    }

    /** Forces [view] hidden when the current appearance says the module owns it. */
    private fun rememberHidden(view: View) {
        synchronized(HIDDEN) {
            for (i in HIDDEN.indices.reversed()) {
                val known = HIDDEN[i].get()
                if (known == null) {
                    HIDDEN.removeAt(i)
                } else if (known === view) {
                    return
                }
            }
            HIDDEN.add(WeakReference(view))
        }
    }

    private fun enforceSuppression(view: View) {
        if (view.isGone) {
            return
        }
        if (!sIdsResolved) {
            resolveIds(view.resources)
        }
        val id = view.id
        // The integer compares come first: this runs on every visibility change in
        // SystemUI, and nothing below may allocate until one matches.
        if (id == 0 ||
            (id != sWifiId && id != sMobileId && id != sBatteryId && id != sClockId)
        ) {
            return
        }
        if (!insideOwnedContainer(view)) {
            return
        }
        if (owned(id, TrioConfig.appearance())) {
            rememberHidden(view)
            hide(view)
        }
    }

    /** Caches the four resource ids the suppression compares against. */
    private fun resolveIds(res: Resources?) {
        if (res == null) {
            return
        }
        sWifiId = id(res, "wifi_combo") ?: 0
        sMobileId = id(res, "mobile_combo") ?: 0
        sBatteryId = id(res, "battery") ?: 0
        sClockId = id(res, "clock") ?: 0
        sMobileGroupId = id(res, "mobile_combo") ?: 0
        sMobileSignalId = id(res, "mobile_signal") ?: 0
        sIdsResolved = true
    }

    /** The bar's contents row, which holds the clock; registered when it attaches. */
    @Volatile
    private var sContents: WeakReference<ViewGroup>? = null

    /**
     * Whether [view] belongs to a place this module draws into: one of the icon rows,
     * or the bar's contents.
     *
     * The same ids are reused by the calling and projection prompts, whose icons have
     * nothing to do with this glyph; hiding one of those would leave a hole with
     * nothing standing in for it.
     */
    private fun insideOwnedContainer(view: View): Boolean {
        val contents = sContents?.get()
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p is ViewGroup && (p === contents || find(p) != null)) {
                return true
            }
            p = (p as? View)?.parent
        }
        return false
    }

    /**
     * Settles every icon this module does or does not replace.
     *
     * The hook alone cannot do it in either direction: it only sees visibility
     * *writes*, and SystemUI never writes `VISIBLE` to a view that is already `GONE`,
     * nor `GONE` to one already visible. Called from the settings change and from
     * mounting a row.
     */
    private fun applyIconVisibility() {
        val a = TrioConfig.appearance()
        // Before the bar's clock goes away, make sure the glyph has one to draw.
        if (a.hidesStatusClock()) {
            ClockTicker.refresh()
        }
        // The bar's clock: the module's own view stands in for the native one whenever
        // seconds or the date line are wanted, and steps aside otherwise.
        syncBarClock()
        // Resolve ids from the containers we have: the showing write may never come.
        if (!sIdsResolved) {
            val res = sContents?.get()?.resources
                ?: snapshot().firstOrNull()?.row?.resources
            resolveIds(res)
        }
        if (!sIdsResolved) {
            return
        }
        for (row in snapshot()) settle(row.container, a)
        sContents?.get()?.let { settle(it, a) }
        // A view hidden while its row was live can outlive it; hand those back too.
        synchronized(HIDDEN) {
            for (i in HIDDEN.indices.reversed()) {
                val view = HIDDEN[i].get()
                if (view == null) {
                    HIDDEN.removeAt(i)
                } else if (!insideOwnedContainer(view)) {
                    release(view)
                    HIDDEN.removeAt(i)
                }
            }
        }
    }

    /** Views hidden by this module, so a detached row cannot leave one behind. */
    private val HIDDEN = ArrayList<WeakReference<View>>()

    /** Walks [root] and puts every icon in it into the state [owned] asks for. */
    private fun settle(root: View, a: TrioAppearance) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                settle(root.getChildAt(i), a)
            }
        }
        val id = root.id
        if (id == sWifiId || id == sMobileId || id == sBatteryId || id == sClockId) {
            if (owned(id, a)) {
                rememberHidden(root)
                hide(root)
            } else {
                release(root)
            }
        }
    }

    /**
     * Learns the current Wi-Fi / mobile levels and the network type.
     *
     * Both icon paths on this ROM funnel through a static `setIconForView`: [HnSignalUtils]
     * for the pipeline views and [HwSignalUtil] for the classic cluster. Both are
     * hooked, and the resource id they are handed is classified by name in
     * [TrioState.noteSignalIcon] - which is also where the network-type icon
     * (`stat_sys_data_connected_5g`) is turned into the label the glyph draws.
     */
    private fun hookSignalIcons(module: XposedModule, cl: ClassLoader): Int {
        var n = 0
        val hn = Refl.cls("com.android.systemui.utils.HnSignalUtils", cl)
        n += hook(
            module, methodNamed(hn, "setIconForView", 2), "honorduo-signal-new",
        ) { chain ->
            val result = chain.proceed()
            noteIcon(chain.getArg(1))
            val view = chain.getArg(0)
            val id = chain.getArg(1)
            if (view is View && id is Int) {
                noteSlotType(view, id)
            }
            result
        }
        val hw = Refl.cls("com.android.systemui.utils.HwSignalUtil", cl)
        n += hook(
            module, methodNamed(hw, "setIconForView", 3), "honorduo-signal-classic",
        ) { chain ->
            val result = chain.proceed()
            noteIcon(chain.getArg(1))
            val view = chain.getArg(0)
            val id = chain.getArg(1)
            if (view is View && id is Int) {
                noteSlotType(view, id)
            }
            result
        }
        if (n == 0) {
            log(module, "no signal icon hook")
        }
        return n
    }

    /**
     * Records one icon id and repaints when it moved a reading.
     *
     * The icons name no SIM, so telephony is asked directly - and only when a per-SIM
     * reading is actually wanted (the glyph's second row, or the out-of-ring stacked
     * reading).
     */
    private fun noteIcon(raw: Any?) {
        if (raw !is Number) {
            return
        }
        val beforeWifi = TrioState.sWifiLevel
        val beforeMobile = TrioState.sMobileLevel
        val beforeType = TrioState.sMobileType
        TrioState.noteSignalIcon(resources(), raw.toInt())
        val simsMoved = (TrioConfig.get().dualSim ||
            TrioConfig.appearance().drawsAnySimRow()) &&
            TrioState.pollSimsNow()
        if (beforeWifi != TrioState.sWifiLevel ||
            beforeMobile != TrioState.sMobileLevel ||
            beforeType != TrioState.sMobileType ||
            simsMoved
        ) {
            invalidateHosts()
        }
    }

    /**
     * Suppresses the per-card network-type icon when the glyph draws the label.
     *
     * `SignalUnitNormalView.updateView` is what shows it (`setVisibility(0)` then the
     * icon), so hiding it after the call is the only place that survives the next
     * refresh. Folding the whole mobile group already covers the common case; this
     * covers the one where the user hands the signal back but keeps the type in the
     * ring.
     */
    private fun hookTypeViews(module: XposedModule, cl: ClassLoader): Int {
        val unit = Refl.cls("com.android.systemui.statusbar.SignalUnitNormalView", cl)
        if (unit == null) {
            log(module, "SignalUnitNormalView missing")
            return 0
        }
        sUnitTypeField = Refl.field(unit, "mMobileType")
        return hook(
            module, methodNamed(unit, "updateView", 2), "honorduo-unit-type",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            val target = chain.getArg(0)
            val a = TrioConfig.appearance()
            if (a.glyph && a.ownsType() && self != null && target != null) {
                val type = Refl.get(sUnitTypeField, self)
                if (type === target && type is View) {
                    hide(type)
                }
            }
            result
        }
    }

    /**
     * Records the bar's contents row, so the clock inside it is settled by the same
     * walk as the icons and needs no list of its own.
     */
    private fun hookContents(module: XposedModule, cl: ClassLoader): Int {
        val contents = Refl.cls("com.android.systemui.statusbar.HwStatusbarContents", cl)
        if (contents == null) {
            log(module, "HwStatusbarContents missing")
            return 0
        }
        return hook(
            module, Refl.method(contents, "onAttachedToWindow"), "honorduo-contents-attach",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                sContents = WeakReference(self)
                applyIconVisibility()
            }
            result
        }
    }

    /**
     * Takes the bar clock's typeface as the module's own, so every reading is drawn in
     * the system's font rather than a lookalike.
     */
    private fun hookClockFace(module: XposedModule, cl: ClassLoader): Int {
        val clock = Refl.cls("com.android.systemui.time.HwStatusBarClock", cl)
        if (clock == null) {
            log(module, "HwStatusBarClock missing")
            return 0
        }
        return hook(
            module, Refl.method(clock, "onAttachedToWindow"), "honorduo-clock-face",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is TextView) {
                if (self.typeface != null) {
                    TrioRenderer.adoptSystemTypeface(self.typeface)
                    invalidateHosts()
                }
                sBarClock = WeakReference(self)
                // A new instance: the old view belongs to the previous row, so remove it.
                dropBarClockNow()
                syncBarClock()
            }
            result
        }
    }

    /** The bar's own clock, when the system is the one drawing the time. */
    private var sBarClock: WeakReference<TextView>? = null

    /** The module's own clock, standing in the bar's clock slot while it is shown. */
    private var sBarClockView: BarClockView? = null

    /** Whether the update loop runs, and its generation (a stale callback is ignored). */
    private var sBarClockLoop = false
    private var sBarClockToken = 0

    /** Mounts, updates or removes the module's clock for the current settings. */
    private fun syncBarClock() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { syncBarClock() }
            return
        }
        try {
            syncBarClockNow()
        } catch (_: Throwable) {
            // A settings change must never take SystemUI down with it. If the mount
            // failed, the next change or the next clock attach tries again.
        }
    }

    private fun syncBarClockNow() {
        val a = TrioConfig.appearance()
        var native = sBarClock?.get()
        if (native == null) {
            val contents = sContents?.get()
            if (contents != null && sClockId != 0) {
                native = contents.findViewById(sClockId) as? TextView
                if (native != null) {
                    sBarClock = WeakReference(native)
                }
            }
        }
        if (native == null || !a.glyph || a.hidesStatusClock() || !a.drawsBarClock()) {
            removeBarClock()
            return
        }
        val view = ensureBarClock(native)
        if (view == null) {
            // Deferred out of a layout pass, or the row is not there yet: retry next frame.
            if (sBarClockView == null && !sBarClockPending) {
                sBarClockPending = true
                native.post {
                    sBarClockPending = false
                    syncBarClock()
                }
            }
            return
        }
        updateBarClock(view, a, native)
        // Repaint unconditionally: the strings may match while the ink or size does not.
        view.invalidate()
        startBarClockLoop(view)
    }

    /** Mounts the module's clock in the native clock's slot, or returns the mounted one. */
    private fun ensureBarClock(native: TextView): BarClockView? {
        val parent = native.parent as? ViewGroup ?: return null
        val existing = sBarClockView
        if (existing != null && existing.parent === parent) {
            // Only one copy: any other in this parent is an orphan from an earlier attach.
            removeStrayClocks(parent, keep = existing)
            return existing
        }
        // Mutating the tree during a layout pass throws: defer by one frame.
        if (parent.isInLayout) {
            if (!sBarClockPending) {
                sBarClockPending = true
                parent.post {
                    sBarClockPending = false
                    syncBarClock()
                }
            }
            return null
        }
        val index = parent.indexOfChild(native)
        if (index < 0) {
            return null
        }
        if (existing != null) {
            removeFromParent(existing)
        }
        removeStrayClocks(parent, keep = null)
        val view = BarClockView(native.context)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.gravity = Gravity.CENTER_VERTICAL
        view.layoutParams = lp
        // Set before addView: attaching re-enters syncBarClock, which must find it.
        sBarClockView = view
        return try {
            parent.addView(view, index, lp)
            allowOverflow(view)
            // Also hidden by the ownership rule; done here so the first frame has one clock.
            hide(native)
            view
        } catch (_: Throwable) {
            sBarClockView = null
            null
        }
    }

    /** Removes the module's clock now, for callers outside a layout pass. */
    private fun dropBarClockNow() {
        sBarClockLoop = false
        sBarClockToken++
        val view = sBarClockView ?: return
        sBarClockView = null
        removeFromParent(view)
        restoreClipping()
    }

    /** Takes the module's clock out of the bar; [owned] shows the native one again. */
    private fun removeBarClock() {
        sBarClockLoop = false
        sBarClockToken++
        val view = sBarClockView ?: return
        sBarClockView = null
        // Posted: callers can be inside a layout pass, where removing a child throws.
        view.post {
            removeFromParent(view)
            restoreClipping()
        }
    }

    /** Lets the two-line block draw past the one-line clock row. */
    private fun allowOverflow(view: View) {
        var p = view.parent
        var depth = 0
        while (p is ViewGroup && depth < BAR_CLOCK_CLIP_DEPTH) {
            if (sBarClockClipped.none { it.group === p }) {
                sBarClockClipped.add(Clipped(p, p.clipChildren, p.clipToPadding))
            }
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
            depth++
        }
    }

    /** One ancestor whose clipping the module turned off, with what it was before. */
    private class Clipped(
        val group: ViewGroup,
        val children: Boolean,
        val padding: Boolean,
    )

    /** The ancestors [allowOverflow] changed, so the change can be undone. */
    private val sBarClockClipped = ArrayList<Clipped>()

    /** Restores the clipping each ancestor had before [allowOverflow]. */
    private fun restoreClipping() {
        for (entry in sBarClockClipped) {
            try {
                entry.group.clipChildren = entry.children
                entry.group.clipToPadding = entry.padding
            } catch (_: Throwable) {
                // detached: nothing to restore
            }
        }
        sBarClockClipped.clear()
    }

    /** True while a mount has been deferred out of a layout pass. */
    private var sBarClockPending = false

    /** Removes every module clock in [parent] except [keep]. */
    private fun removeStrayClocks(parent: ViewGroup, keep: BarClockView?) {
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child is BarClockView && child !== keep) {
                try {
                    parent.removeViewAt(i)
                } catch (_: Throwable) {
                    // already gone
                }
            }
        }
    }

    private fun removeFromParent(view: View) {
        try {
            (view.parent as? ViewGroup)?.removeView(view)
        } catch (_: Throwable) {
            // A row torn down under us is not worth crashing over.
        }
    }

    /** Writes the current strings and ink into the module's clock. */
    private fun updateBarClock(view: BarClockView, a: TrioAppearance, native: TextView) {
        val texts = ClockTicker.barTexts(
            a.clockDate, a.clockWeekday, a.clockDateFormat, a.clockWeekdayFormat,
        )
        val time = texts[0]
        val date = texts[1]
        val weekday = texts[2]
        val lunar = if (a.clockLunar) texts[3] else ""
        view.bindInk(native)
        val userScale = a.clockSize / Prefs.DEF_CLOCK_SIZE.toFloat()
        val baseSize = native.textSize * userScale
        val density = view.resources.displayMetrics.density
        val isDark = TrioRenderer.isDarkBackground(native.currentTextColor)
        val wkColor = if (isDark) a.clockWeekdayColorOnDark else a.clockWeekdayColorOnLight
        val dtColor = if (isDark) a.clockDateColorOnDark else a.clockDateColorOnLight
        val lnColor = if (isDark) a.clockLunarColorOnDark else a.clockLunarColorOnLight
        view.apply(
            time, date, weekday, lunar, a.clockWeight,
            native.currentTextColor, wkColor, dtColor, lnColor,
            baseSize, barHeight(view),
            a.clockTopOffsetX * density, a.clockBottomOffsetX * density,
            a.clockLunarGap * density, a.clockRowGap * density,
        )
        view.requestLayout()
        (view.parent as? View)?.requestLayout()
    }

    /** The status bar's own height: the box the block is allowed to fill. */
    @SuppressLint("InternalInsetResource")
    private fun barHeight(view: View): Int {
        val id = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) view.resources.getDimensionPixelSize(id) else 0
    }

    /**
     * Dynamically avoids system notification capsules (Smart Capsule / Live Window / Island).
     *
     * In MagicOS, when a capsule appears/expands on the status bar, it occupies the area up to
     * [capsuleRight]. We inspect our own views in the row using their absolute on-screen positions:
     * - ONLY views that are actually encroached/covered by the capsule have alpha set to 0f.
     * - Views that are still to the right of the capsule boundary stay completely visible (alpha = 1f).
     * - When the capsule retracts, all views are restored to alpha = 1f.
     */
    private val ALPHA_GROUP_NAMES = arrayOf("statusIcons", "network_area_icons")
    @Volatile private var sAlphaGroupIds: IntArray? = null

    /**
     * Smart physical avoidance and rescue scheme
     */
    private fun handleCapsuleAvoidanceAndRescue(rowView: ViewGroup, isShown: Boolean, capsuleRight: Int) {
        val row = find(rowView) ?: return
        val density = rowView.resources.displayMetrics.density
        val safeGap = (4f * density).toInt()
        val avoidBound = capsuleRight + safeGap
        val loc = IntArray(2)

        // 1. Rescue falsely faded status icons and network icons
        val ids = sAlphaGroupIds ?: run {
            val res = rowView.context.resources
            val pkg = rowView.context.packageName
            ALPHA_GROUP_NAMES.mapNotNull { name ->
                res.getIdentifier(name, "id", pkg).takeIf { it != 0 }
            }.toIntArray().also { sAlphaGroupIds = it }
        }
        for (id in ids) {
            val group = rowView.findViewById<ViewGroup>(id) ?: continue
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i) ?: continue
                if (child.visibility != View.VISIBLE) continue
                if (!isShown || capsuleRight <= 0) {
                    if (child.alpha != 1f) child.alpha = 1f
                } else {
                    child.getLocationOnScreen(loc)
                    val leftOnScreen = loc[0]
                    if (leftOnScreen >= avoidBound) {
                        // Safely outside the capsule boundary: restore visibility
                        if (child.alpha != 1f) child.alpha = 1f
                    }
                }
            }
        }

        // 2. Per-view collision avoidance for module's own views
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
                    // Truly encroached by the capsule: hide to avoid collision
                    v.alpha = 0f
                    v.translationX = 0f
                } else {
                    // Safe in clear area: keep fully visible
                    v.alpha = 1.0f
                    v.translationX = 0f
                }
            }
        }
    }

    private fun checkCapsuleAvoidanceAndRescue(rowView: ViewGroup) {
        val isCapsule = (Refl.get(sIsCapsuleShownField, rowView) as? Boolean) ?: false
        val isColored = (Refl.get(sIsColoredCapsuleShownField, rowView) as? Boolean) ?: false
        val right = (Refl.get(sCapsuleRightField, rowView) as? Number)?.toInt() ?: 0
        if ((isCapsule || isColored) && right > 0) {
            handleCapsuleAvoidanceAndRescue(rowView, true, right)
        } else {
            handleCapsuleAvoidanceAndRescue(rowView, false, 0)
        }
    }

    private val BURN_IN_CELLS = arrayOf(
        0 to 0, 1 to 0, 1 to 1, 0 to 1,
        -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
    )

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

    private fun resetBurnIn(row: Row) {
        val views = listOfNotNull(row.glyph, row.signal, row.type, row.perf, row.fps)
        for (v in views) {
            if (v.parent === row.container) {
                v.translationX = 0f
                v.translationY = 0f
            }
        }
    }

    /** (Re)arms the update loop, always restarting so the cadence follows the settings. */
    private fun startBarClockLoop(view: BarClockView) {
        sBarClockLoop = true
        val token = ++sBarClockToken
        val handler = view.handler ?: Handler(Looper.getMainLooper())
        handler.postDelayed({ stepBarClock(token) }, nextBarClockDelay())
    }

    private fun stepBarClock(token: Int) {
        if (!sBarClockLoop || token != sBarClockToken) {
            return
        }
        val view = sBarClockView
        if (view == null || !view.isAttachedToWindow) {
            // Between attach and detach; the view restarts the loop when it comes back.
            sBarClockLoop = false
            return
        }
        val a = TrioConfig.appearance()
        val native = sBarClock?.get()
        if (native == null || !a.glyph || a.hidesStatusClock() || !a.drawsBarClock()) {
            removeBarClock()
            return
        }
        try {
            updateBarClock(view, a, native)
        } catch (_: Throwable) {
            // One bad frame must not take the loop down with it.
        } finally {
            // Re-armed in a finally, and only while this generation is current.
            if (sBarClockLoop && token == sBarClockToken) {
                val handler = view.handler ?: Handler(Looper.getMainLooper())
                handler.postDelayed({ stepBarClock(token) }, nextBarClockDelay())
            }
        }
    }

    /** Milliseconds to the next moment the clock's text can change. */
    private fun nextBarClockDelay(): Long {
        val a = TrioConfig.appearance()
        val now = Calendar.getInstance()
        if (a.clockSeconds) {
            return maxOf(1L, 1000L - now.get(Calendar.MILLISECOND))
        }
        val elapsed = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
        return maxOf(1L, 60_000L - elapsed + BAR_CLOCK_MINUTE_LAG_MS)
    }

    /**
     * The bar clock, drawn by the module.
     *
     * A plain `View`, because the ROM's clock is a one-line `TextView`: a two-line string
     * in it is clipped to one line. This measures both lines itself and paints them
     * centred.
     */
    private class BarClockView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var time = ""
        private var date = ""
        private var weekday = ""
        private var lunar = ""
        private var weight = Prefs.DEF_CLOCK_WEIGHT
        private var ink = 0
        private var weekdayColor = 0
        private var dateColor = 0
        private var lunarColor = 0

        /** The ROM clock whose ink this view copies, read live on every frame. */
        private var inkHost: WeakReference<TextView>? = null

        private var baseSize = 0f
        private var maxHeight = 0
        private var topOffsetX = 0f
        private var bottomOffsetX = 0f
        private var lunarGap = 0f
        private var rowGap = 0f

        private var timeSize = 0f
        private var lineSize = 0f
        private var blockWidth = 0f
        private var blockHeight = 0f

        fun bindInk(host: TextView) {
            inkHost = WeakReference(host)
        }

        fun refreshInk() {
            val host = inkHost?.get() ?: return
            ink = host.currentTextColor
            invalidate()
        }

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun apply(
            time: String, date: String, weekday: String, lunar: String,
            weight: Int, ink: Int,
            weekdayColor: Int, dateColor: Int, lunarColor: Int,
            baseSize: Float, maxHeight: Int,
            topOffsetX: Float, bottomOffsetX: Float,
            lunarGap: Float, rowGap: Float,
        ) {
            if (this.time == time && this.date == date && this.weekday == weekday &&
                this.lunar == lunar && this.weight == weight &&
                this.ink == ink && this.weekdayColor == weekdayColor &&
                this.dateColor == dateColor && this.lunarColor == lunarColor &&
                this.baseSize == baseSize && this.maxHeight == maxHeight &&
                this.topOffsetX == topOffsetX && this.bottomOffsetX == bottomOffsetX &&
                this.lunarGap == lunarGap && this.rowGap == rowGap
            ) {
                return
            }
            val shapeChanged = this.time.length != time.length ||
                this.date != date ||
                this.weekday != weekday ||
                this.lunar != lunar ||
                this.weight != weight ||
                this.baseSize != baseSize ||
                this.maxHeight != maxHeight ||
                this.topOffsetX != topOffsetX ||
                this.bottomOffsetX != bottomOffsetX ||
                this.lunarGap != lunarGap ||
                this.rowGap != rowGap
            this.time = time
            this.date = date
            this.weekday = weekday
            this.lunar = lunar
            this.weight = weight
            this.ink = ink
            this.weekdayColor = weekdayColor
            this.dateColor = dateColor
            this.lunarColor = lunarColor
            this.baseSize = baseSize
            this.maxHeight = maxHeight
            this.topOffsetX = topOffsetX
            this.bottomOffsetX = bottomOffsetX
            this.lunarGap = lunarGap
            this.rowGap = rowGap
            // Force recalculate solve layout metrics immediately so date format change updates on screen
            val targetW = if (width > 0) width else (resources.displayMetrics.widthPixels * BAR_CLOCK_WIDTH_SHARE).toInt()
            solve(targetW)
            requestLayout()
            (parent as? View)?.requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availW = MeasureSpec.getSize(widthMeasureSpec)
            solve(availW)
            val wantedW = ceil(blockWidth).toInt() + paddingLeft + paddingRight
            val wantedH = ceil(blockHeight).toInt() + paddingTop + paddingBottom
            val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY &&
                availW > 0
            ) {
                availW
            } else {
                max(1, wantedW)
            }
            setMeasuredDimension(width, max(1, wantedH))
        }

        private fun solve(availW: Int) {
            if (time.isEmpty() || baseSize <= 0f) {
                timeSize = 0f
                lineSize = 0f
                blockWidth = 0f
                blockHeight = 0f
                return
            }
            paint.typeface = TrioRenderer.typefaceFor(weight)
            var scale = 1f
            val hasBottom = date.isNotEmpty() || lunar.isNotEmpty()
            val effectiveRowGap = max(rowGap, gap(baseSize))
            if (maxHeight > 0) {
                val needed = lineHeight(baseSize) +
                    (if (hasBottom) lineHeight(baseSize * ClockTicker.BAR_DATE_SCALE) + effectiveRowGap else 0f)
                val room = maxHeight.toFloat()
                if (needed > room) {
                    scale = room / needed
                }
            }
            val budget = if (availW > 0) {
                availW.toFloat()
            } else {
                resources.displayMetrics.widthPixels * BAR_CLOCK_WIDTH_SHARE
            }
            timeSize = baseSize * scale
            lineSize = timeSize * ClockTicker.BAR_DATE_SCALE

            paint.textSize = lineSize
            val wkW = if (weekday.isEmpty()) 0f else paint.measureText(weekday) + gap(lineSize)
            val dtW = if (date.isEmpty()) 0f else paint.measureText(date)
            val effectiveLunarGap = if (date.isNotEmpty() && lunar.isNotEmpty()) max(lunarGap, gap(lineSize)) else 0f
            val lnW = if (lunar.isEmpty()) 0f else paint.measureText(lunar)
            paint.textSize = timeSize
            val tmW = paint.measureText(widestSample(time))
            val topW = wkW + tmW
            val botW = dtW + effectiveLunarGap + lnW
            // The measured box deliberately ignores the two offsets. Folding them in made
            // the view itself wider, so nudging the top row moved the bottom row too: the
            // block was re-centred around a box that had changed shape. Offsets now only
            // translate their own row inside a box that stays put.
            var widest = max(topW, botW)
            if (budget > 0 && widest > budget) {
                val s2 = budget / widest
                timeSize *= s2
                lineSize *= s2
                widest = budget
            }
            blockWidth = widest
            blockHeight = lineHeight(timeSize) +
                (if (hasBottom) lineHeight(lineSize) + effectiveRowGap else 0f)
        }

        private fun widestSample(text: String): String {
            if (text.isEmpty()) return text
            val sb = StringBuilder(text.length)
            for (ch in text) sb.append(if (ch.isDigit()) '8' else ch)
            return sb.toString()
        }

        private fun lineHeight(size: Float): Float {
            if (size <= 0f) return 0f
            paint.textSize = size
            val metrics = paint.fontMetrics
            return (metrics.descent - metrics.ascent) * BAR_CLOCK_LINE_SPACING
        }

        private fun gap(size: Float): Float = size * BAR_CLOCK_GAP

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            syncBarClock()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            if (sBarClockView === this) {
                sBarClockLoop = false
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (time.isEmpty() || timeSize <= 0f) {
                return
            }
            paint.typeface = TrioRenderer.typefaceFor(weight)
            val currentInk = inkHost?.get()?.currentTextColor ?: ink
            val wkDrawColor = if (weekdayColor != 0) weekdayColor else currentInk
            val dtDrawColor = if (dateColor != 0) dateColor else currentInk
            val lnDrawColor = if (lunarColor != 0) lunarColor else currentInk

            val timeH = lineHeight(timeSize)
            val lineH = lineHeight(lineSize)
            val effectiveRowGap = max(rowGap, gap(timeSize))
            val hasBottom = date.isNotEmpty() || lunar.isNotEmpty()
            val totalH = timeH + (if (hasBottom) lineH + effectiveRowGap else 0f)
            var top = (paddingTop + height - paddingBottom) / 2f - totalH / 2f
            val cx = (paddingLeft + width - paddingRight) / 2f

            paint.textSize = lineSize
            val wkW = if (weekday.isEmpty()) 0f else paint.measureText(weekday) + gap(lineSize)
            val dtW = if (date.isEmpty()) 0f else paint.measureText(date)
            val effectiveLunarGap = if (date.isNotEmpty() && lunar.isNotEmpty()) max(lunarGap, gap(lineSize)) else 0f
            val lnW = if (lunar.isEmpty()) 0f else paint.measureText(lunar)
            paint.textSize = timeSize
            val tmW = paint.measureText(time)
            val topW = wkW + tmW
            val botW = dtW + effectiveLunarGap + lnW

            // Top row: Weekday + Time
            val topStartX = cx - topW / 2f + topOffsetX
            var currX = topStartX
            paint.textAlign = Paint.Align.LEFT
            if (weekday.isNotEmpty()) {
                paint.textSize = lineSize
                paint.color = wkDrawColor
                canvas.drawText(weekday, currX, top + (timeH - lineH) - paint.fontMetrics.ascent, paint)
                currX += paint.measureText(weekday) + gap(lineSize)
            }
            paint.textSize = timeSize
            paint.color = currentInk
            canvas.drawText(time, currX, top - paint.fontMetrics.ascent, paint)

            // Bottom row: Date + Lunar
            if (hasBottom) {
                top += timeH + effectiveRowGap
                val botStartX = cx - botW / 2f + bottomOffsetX
                var botX = botStartX
                paint.textSize = lineSize
                if (date.isNotEmpty()) {
                    paint.color = dtDrawColor
                    canvas.drawText(date, botX, top - paint.fontMetrics.ascent, paint)
                    botX += paint.measureText(date) + effectiveLunarGap
                }
                if (lunar.isNotEmpty()) {
                    paint.color = lnDrawColor
                    canvas.drawText(lunar, botX, top - paint.fontMetrics.ascent, paint)
                }
            }
        }
    }

    /**
     * Replaces `HnPhoneStatusBarAnimController.setPivotXY` with a null-safe copy.
     *
     * The ROM walks two status-bar contents trees by index, using the *second* one's
     * child count for both. This module adds one child (its clock) to the live tree and
     * not to the copy the controller holds, so the walk runs off the end of the shorter
     * one, `getChildAt` returns null, and `childAt.getPivotX()` kills SystemUI on every
     * pull-down. The copy below walks the shorter length and skips missing children, so
     * the pull animation keeps working instead of being skipped.
     */
    private fun hookPivotXY(module: XposedModule, cl: ClassLoader): Int {
        val controllerCls = Refl.cls("com.android.systemui.statusbar.phone.HnPhoneStatusBarAnimController", cl)
            ?: return 0
        val contentsCls = Refl.cls("com.android.systemui.statusbar.HwStatusbarContents", cl)
        val method = methodNamed(controllerCls, "setPivotXY", 3) ?: return 0
        sOperatorWidth = Refl.field(controllerCls, "mOperatorViewWidth")
        sPaddingLeft = Refl.field(contentsCls, "mCurrentPaddingLeft")
        sPaddingRight = Refl.field(contentsCls, "mCurrentPaddingRight")
        return hook(module, method, "honorduo-pivot") { chain ->
            val first = chain.getArg(0) as? ViewGroup
            val second = chain.getArg(1) as? ViewGroup
            if (first != null && second != null) {
                setPivotXY(
                    chain.thisObject, first, second,
                    chain.getArg(2) as? Boolean ?: false,
                )
            }
            null
        }
    }

    private var sCapsuleRightField: Field? = null
    private var sIsCapsuleShownField: Field? = null
    private var sIsColoredCapsuleShownField: Field? = null
    private var sOperatorWidth: Field? = null
    private var sPaddingLeft: Field? = null
    private var sPaddingRight: Field? = null

    /** The ROM's pivot pass, with the child walk bounded by the shorter tree. */
    private fun setPivotXY(controller: Any?, a: ViewGroup, b: ViewGroup, operator: Boolean) {
        val res = a.resources
        val rtl = b.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val clock = id(res, "clock")?.let { b.findViewById<View>(it) }
        val left = if (rtl) Refl.getInt(sPaddingLeft, b, 0) else clock?.left ?: 0
        val right = if (rtl) clock?.right ?: 0 else b.width - Refl.getInt(sPaddingRight, b, 0)
        val operatorId = id(res, "operator_name_container") ?: 0
        val systemIconId = id(res, "system_icon_area") ?: 0
        val operatorWidth = Refl.getInt(sOperatorWidth, controller, 0).toFloat()
        for (i in 0 until min(a.childCount, b.childCount)) {
            val from = a.getChildAt(i) ?: continue
            val to = b.getChildAt(i) ?: continue
            val mirrored = (to.layoutDirection == View.LAYOUT_DIRECTION_RTL) ==
                (to.id == systemIconId)
            from.pivotX = when {
                !mirrored && operator && from.id == operatorId -> operatorWidth
                !mirrored -> right - to.left.toFloat()
                operator && from.id == operatorId -> 0f
                else -> left - to.left.toFloat()
            }
            from.pivotY = to.height / 2f
        }
    }

    /**
     * Repaints when the status bar re-tints.
     *
     * `HwStatusbarContents.refreshTint()` is what moves the bar's ink between light and
     * dark backgrounds; without this the glyph keeps the previous background's colour
     * until some unrelated battery or signal event happens to repaint it.
     */
    private fun hookTint(module: XposedModule, cl: ClassLoader): Int {
        val contents = Refl.cls("com.android.systemui.statusbar.HwStatusbarContents", cl)
        if (contents == null) {
            log(module, "HwStatusbarContents missing")
            return 0
        }
        return hook(
            module, Refl.method(contents, "refreshTint"), "honorduo-tint",
        ) { chain ->
            val result = chain.proceed()
            invalidateHosts()
            // Posted: the tint lands after refreshTint returns, so inline would read the old ink.
            sBarClockView?.post { sBarClockView?.refreshInk() }
            result
        }
    }

    /**
     * Repaints every glyph and re-reads the out-of-ring readings.
     *
     * The row is asked to lay out again as well: the glyph's own width follows the
     * arrangement and the readings follow the signal, so the row has to re-measure
     * rather than only redraw.
     */
    fun invalidateHosts() {
        // The bar clock's ink follows the bar's own tint, and this is the hook every
        // other reading already uses for that event.
        sBarClockView?.postInvalidateOnAnimation()
        for (row in snapshot()) {
            row.row.post {
                row.glyph?.invalidate()
                syncOutViews(row)
                row.row.requestLayout()
            }
        }
    }

    private fun hide(view: Any?) {
        if (view !is View || view.isGone) {
            return
        }
        // Not recorded when already GONE: that GONE may be the module's own earlier write.
        if (!writingSuppressed()) noteRequested(view)
        suppressWrite { view.visibility = View.GONE }
    }

    private fun show(view: Any?) {
        if (view is View && view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
        }
    }

    /**
     * The visibility SystemUI last asked for, per icon this module takes over.
     *
     * "Hand it back" cannot mean "make it visible": the native Wi-Fi icon is `GONE`
     * while the radio is off, and showing it would put an icon on the bar for a network
     * that is not connected. The view's own visibility is no help - the module has been
     * overwriting it.
     */
    private val REQUESTED = WeakHashMap<View, Int>()

    /** Set while the module writes a visibility itself, so its own GONE is not recorded. */
    private val sSuppressWrite = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    /** Whether the module itself is the one writing a visibility right now. */
    private fun writingSuppressed(): Boolean = sSuppressWrite.get() == true

    private inline fun suppressWrite(block: () -> Unit) {
        val previous = writingSuppressed()
        sSuppressWrite.set(true)
        try {
            block()
        } finally {
            sSuppressWrite.set(previous)
        }
    }

    /** Records the system's own request for one of the icons the module replaces. */
    private fun noteRequested(view: View) {
        // Resolved ids only: [enforceSuppression] sets them from the first row.
        if (!sIdsResolved) {
            return
        }
        val id = view.id
        if (id == 0 || (id != sWifiId && id != sMobileId && id != sBatteryId && id != sClockId)) {
            return
        }
        synchronized(REQUESTED) { REQUESTED[view] = view.visibility }
    }

    /**
     * Hands an icon back in the state the system last asked for.
     *
     * With nothing remembered the view is left alone: the system's next write settles it.
     */
    private fun release(view: View) {
        val wanted = synchronized(REQUESTED) { REQUESTED[view] } ?: View.VISIBLE
        if (view.visibility == wanted) {
            return
        }
        suppressWrite { view.visibility = wanted }
    }

    /**
     * A child of [root] by resource name, or null.
     *
     * Resolved from the view's own resources rather than from a row: this is called
     * while the first row is being registered, when no row exists yet.
     */
    private fun idView(root: View, name: String): View? {
        val id = id(root.resources, name) ?: return null
        return try {
            root.findViewById(id)
        } catch (t: Throwable) {
            null
        }
    }

    private fun id(res: Resources, name: String): Int? {
        synchronized(IDS) {
            val cached = IDS[name]
            if (cached != null) {
                return if (cached != 0) cached else null
            }
            val id = try {
                res.getIdentifier(name, "id", "com.android.systemui")
            } catch (t: Throwable) {
                return null
            }
            IDS[name] = id
            return if (id != 0) id else null
        }
    }

    /** Resources handle for resolving view ids and signal icon entry names. */
    private fun resources(): Resources? {
        for (row in snapshot()) {
            try {
                return row.row.resources
            } catch (ignored: Throwable) {
                // try the next row
            }
        }
        return null
    }

    /** The declared method with this name and arity, or null. */
    private fun methodNamed(c: Class<*>?, name: String, arity: Int): Method? {
        if (c == null) {
            return null
        }
        for (m in c.declaredMethods) {
            if (name == m.name && m.parameterTypes.size == arity) {
                m.isAccessible = true
                return m
            }
        }
        return null
    }

    /**
     * Hooks one executable, tolerating a missing method (null) or a rejected hook.
     * Returns 1 when the hook was installed, 0 otherwise.
     */
    private fun hook(
        module: XposedModule, m: Method?, id: String,
        hooker: XposedInterface.Hooker,
    ): Int {
        if (m == null) {
            log(module, "skip $id: method not found")
            return 0
        }
        return try {
            module.hook(m).setId(id).intercept(hooker)
            1
        } catch (t: Throwable) {
            log(module, "skip $id: $t")
            0
        }
    }

    /** The out-of-ring network type label. */
    private class OutTypeLabel(context: Context) : TextView(context) {
        /** The row state supplying the ink. */
        @JvmField var host: TrioState? = null

        /**
         * The suffix scale the current text was built with; -1 until first applied.
         * Remembered because a scale change leaves the *characters* identical, so a
         * content comparison alone would skip the rebuild.
         */
        @JvmField var suffixScale: Int = -1

        init {
            gravity = Gravity.CENTER
            isSingleLine = true
            includeFontPadding = false
            letterSpacing = TrioGeometry.VALUE_LETTER_SPACING
        }
    }

    /** The out-of-ring performance readout: charging power and battery temperature. */
    private class PerfLabel(context: Context) : TextView(context) {
        /** The row state supplying the ink. */
        @JvmField var host: TrioState? = null

        init {
            gravity = Gravity.CENTER
            includeFontPadding = false
            letterSpacing = TrioGeometry.VALUE_LETTER_SPACING
        }
    }

    /** Records which card an icon belongs to, for the per-card type reading. */
    private fun noteSlotType(view: View, resId: Int) {
        val type = TrioState.typeOfIcon(view.resources, resId) ?: return
        val group = mobileGroupOf(view) ?: return
        if (group.childCount !in 1..TrioState.SIM_SLOTS) return
        var child: View = view
        while (child.parent !== group) {
            child = child.parent as? View ?: return
        }
        TrioState.setSlotType(group.indexOfChild(child), type)
    }

    /** The `mobile_combo` group an icon sits in, when it is in one. */
    private fun mobileGroupOf(view: View): ViewGroup? {
        if (!sIdsResolved || sMobileGroupId == 0) {
            return null
        }
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p is ViewGroup && p.id == sMobileGroupId) {
                return p
            }
            p = (p as? View)?.parent
        }
        return null
    }

    /**
     * The out-of-ring SIM reading: four ascending capsule bars, and the dot row beneath
     * them when both SIMs have a reading.
     *
     * A view of our own rather than Honor's signal icon: that icon is one icon for one
     * reading, while this mode draws a reading per SIM and has to place them itself.
     */
    private class OutSignalView(context: Context) : View(context) {

        init {
            // Never a touch target; see [BarClockView].
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        @JvmField var host: TrioState? = null

        /** Bars to light, `0..`[TrioGeometry.STACK_COLUMNS]. */
        @JvmField var bars: Int = 0

        /** Dots to light, or `-1` when there is no second reading. */
        @JvmField var dots: Int = -1

        /** Each card's network type, when the labels are switched on. */
        @JvmField var type: String? = null

        @JvmField var type2: String? = null

        override fun onDraw(canvas: Canvas) {
            val source = host ?: return
            // Read on every frame rather than cached.
            TrioRenderer.drawOutSignal(
                canvas, width, height, bars, dots,
                source.foreground(), TrioConfig.appearance(), type, type2,
            )
        }
    }

    private fun log(module: XposedModule, msg: String) {
        try {
            module.log(LOG_INFO, TAG, msg)
        } catch (ignored: Throwable) {
            // logging must never break hook installation.
        }
    }

    /** Logs through the handle captured at install time. */
    fun log(priority: Int, msg: String) {
        val module = sModule ?: return
        try {
            module.log(priority, TAG, msg)
        } catch (ignored: Throwable) {
            // logging must never break a config change.
        }
    }

    /** True when the user asked for verbose logging. */
    fun debugLog(): Boolean = TrioConfig.debugLog()
}
