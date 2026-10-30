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

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.TextView
import androidx.core.view.isGone
import io.github.odomu.honorduo.hook.core.Refl
import io.github.odomu.honorduo.hook.tracker.WifiPresence
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.StatusAppearance
import java.lang.ref.WeakReference
import java.lang.reflect.Field

/**
 * Manages resource id resolution, ownership arbitration, suppression (hide),
 * and restoration (release) of native status bar icons (battery, wifi, mobile, clock).
 */
internal object StatusBarIconManager {

    /** Cached ids of the views the module suppresses; 0 until resolved. */
    @Volatile var wifiId = 0
        private set
    @Volatile var mobileId = 0
        private set
    @Volatile var batteryId = 0
        private set
    @Volatile var clockId = 0
        private set

    /** The native signal group and the icon inside each card. */
    @Volatile var mobileGroupId = 0
        private set
    @Volatile var mobileSignalId = 0
        private set

    /**
     * Slot names of the status icons the user can drop outright.
     *
     * Read from the framework's own strings rather than hardcoded: `PhoneStatusBarPolicy`
     * builds the alarm's slot from the same resource, so the two cannot drift.
     */
    @Volatile private var alarmSlot: String = ""
    @Volatile private var bluetoothSlot: String = ""

    @Volatile var idsResolved = false
        private set

    /**
     * Every `HwStatusbarContents` registered at attachment, in attachment order.
     * Main and overlay bars contain a clock; the notch header does not. Detached
     * trees can remain reachable, so selection must check attachment explicitly.
     */
    private val CONTENTS = ArrayList<WeakReference<ViewGroup>>()

    /** The bar's contents row, which holds the clock; registered when it attaches. */
    @Volatile
    var contents: WeakReference<ViewGroup>? = null

    /** The best live bar tree: prefer the currently shown contents that owns the clock. */
    val contentsViewGroup: ViewGroup?
        get() = synchronized(CONTENTS) {
            var fallback: ViewGroup? = null
            var clockContents: ViewGroup? = null
            for (i in CONTENTS.indices.reversed()) {
                val group = CONTENTS[i].get()
                if (group == null || !group.isAttachedToWindow) {
                    CONTENTS.removeAt(i)
                    continue
                }
                if (fallback == null) fallback = group
                if (clockId == 0 || group.findViewById<View>(clockId) == null) continue
                if (group.isShown) return@synchronized group
                if (clockContents == null) clockContents = group
            }
            clockContents ?: fallback
        }

    /**
     * Whether [view] belongs to the lock screen's own status bar.
     *
     * Found by walking up to the bar root: `keyguard_status_bar.xml` inflates its own
     * `HwKeyguardStatusBarView`, so the class name is the one signal that distinguishes
     * the lock screen's bar from the main one without depending on a visibility read.
     */
    fun isKeyguardTree(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.javaClass.name.endsWith("KeyguardStatusBarView")) return true
            current = current.parent as? View
        }
        return false
    }

    /**
     * The contents tree the module may draw its own clock into, or null when only the
     * lock screen's bar is present.
     *
     * Separate from [contentsViewGroup] on purpose. That one answers "where is the bar
     * right now" and is used for icon settlement, which the lock screen does need - it
     * shows the merged glyph. The clock is the one piece the lock screen does not want:
     * it has its own time, so a second clock there is a duplicate, and mounting it there
     * also stole the main bar's copy.
     */
    val clockHostViewGroup: ViewGroup?
        get() = synchronized(CONTENTS) {
            var fallback: ViewGroup? = null
            var clockContents: ViewGroup? = null
            for (i in CONTENTS.indices.reversed()) {
                val group = CONTENTS[i].get()
                if (group == null || !group.isAttachedToWindow || isKeyguardTree(group)) continue
                if (fallback == null) fallback = group
                if (clockId == 0 || group.findViewById<View>(clockId) == null) continue
                if (group.isShown) return@synchronized group
                if (clockContents == null) clockContents = group
            }
            clockContents ?: fallback
        }


    /** Registers one attached contents view; called for every one, not only the last. */
    fun noteContents(group: ViewGroup) {
        synchronized(CONTENTS) {
            CONTENTS.removeAll { it.get() == null || it.get() === group }
            CONTENTS.add(WeakReference(group))
        }
        contents = WeakReference(group)
    }

    /**
     * Views hidden by this module, with the visibility the host had given each of
     * them. Restoring that instead of forcing VISIBLE is what keeps a lock-screen
     * rebuild from blinking: the host hides its own icons there, and a blind VISIBLE
     * fought every one of those writes.
     */
    private val HIDDEN = ArrayList<Hidden>()

    private class Hidden(val view: WeakReference<View>, val previous: Int)

    /** Views hidden because of their slot, with the visibility they had before. */
    private val slotHidden = ArrayList<SlotHide>()

    private class SlotHide(val view: WeakReference<View>, val previous: Int)

    /** `mSlot` per icon view class; one entry in practice. */
    private val slotFields = HashMap<Class<*>, Field?>(2)

    private const val STATUS_ICON_VIEW_SUFFIX = "StatusBarIconView"

    /** The bar's own clock view, whose visibility is owned by the arrangement. */
    private const val STATUS_BAR_CLOCK_SUFFIX = "HwStatusBarClock"

    /** The stylus icon the bar also draws from the Bluetooth slot family. */
    private const val BLUETOOTH_PENCIL_SLOT = "bluetooth_pencil"

    /** The two carrier badges that draw the same "HD" mark, one per network path. */
    private const val VOLTE_SLOT = "volte_call"
    private const val UNICOM_SLOT = "unicom_call"
    private const val VOWIFI_SLOT = "vowifi_call"

    /** Caches the resource ids the suppression compares against. */
    fun resolveIds(res: Resources?) {
        if (res == null) return
        wifiId = id(res, "wifi_combo")
        mobileId = id(res, "mobile_combo")
        batteryId = id(res, "battery")
        clockId = id(res, "clock")
        mobileGroupId = id(res, "mobile_combo")
        mobileSignalId = id(res, "mobile_signal")
        alarmSlot = frameworkString(res, "status_bar_alarm_clock")
        bluetoothSlot = frameworkString(res, "status_bar_bluetooth")
        idsResolved = true
    }

    /** A framework string by name, or "" when this ROM does not have it. */
    private fun frameworkString(res: Resources, name: String): String {
        val key = Refl.resId(res, name, "string", "android")
        return if (key != 0) res.getString(key) else ""
    }

    private fun id(res: Resources, name: String): Int = Refl.resId(res, name)

    /**
     * The one ownership rule: which status-bar icon this module replaces right now.
     *
     * The visibility hook, the settings change and mounting a row all ask this and
     * nothing else, so hiding an icon and handing it back cannot disagree.
     */
    fun isOwned(id: Int, a: StatusAppearance): Boolean = when {
        !a.enabled || StatusBarRowManager.suppressed -> false
        id == wifiId -> a.glyph && a.ownsWifi()
        id == mobileId -> (a.glyph && (a.signalDots() || a.drawsAnySimRow())) || a.stackedOut()
        id == batteryId -> a.glyph
        id == clockId -> a.barClockHidden || (a.glyph && a.hidesStatusClock()) || BarClockManager.isClockAttached
        else -> false
    }

    /** Walks [root] and puts every icon in it into the state [isOwned] asks for. */
    fun settle(root: View, a: StatusAppearance) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                settle(root.getChildAt(i), a)
            }
        }
        val id = root.id
        if (id == wifiId || id == mobileId || id == batteryId || id == clockId) {
            // The lock screen draws its own time and keeps its own clock: it is never
            // taken over, only handed back. The row suppression still applies to the
            // merged glyph, which is what the lock screen is meant to show.
            if (isOwned(id, a) && !isKeyguardClock(root)) {
                rememberHidden(root)
                hide(root)
            } else {
                release(root, a)
            }
            return
        }
        settleSlotIcon(root, a)
    }

    /**
     * Hides an alarm/Bluetooth icon by its slot.
     *
     * These two are not reachable through the id table: the host builds them in
     * `PhoneStatusBarPolicy.updateAlarm()` / `updateBluetooth()`, each of which resolves
     * its drawable once and hands the view to the row. Swapping the drawable therefore
     * only lands at boot or at the next state change, so the switch has to act on the
     * view - and only on views this module hid, or turning the switch off would make an
     * icon the host itself had hidden reappear.
     */
    /**
     * Whether the settings ask for the slot [view] carries to be hidden.
     *
     * Shared by the settle pass and the visibility hook, so the bar the keyguard rebuilds
     * - which is walked by neither the row list nor `HwStatusbarContents` - is answered
     * by the same rule instead of being handed back as visible.
     */
    fun hidesSlot(view: View, a: StatusAppearance): Boolean {
        if (isOperatorHdIcon(view)) {
            return a.hideHdVolte || a.hideHdVowifi
        }
        val slot = slotOf(view) ?: return false
        if (a.hideAlarmIcon && (slot == "alarm_clock" || (alarmSlot.isNotEmpty() && slot == alarmSlot))) return true
        if (a.hideBluetoothIcon && (slot == bluetoothSlot || slot == BLUETOOTH_PENCIL_SLOT)) return true
        if (a.hideHdVolte && (slot == VOLTE_SLOT || slot == UNICOM_SLOT)) return true
        if (a.hideHdVowifi && slot == VOWIFI_SLOT) return true
        return false
    }

    fun isOperatorHdIcon(view: View): Boolean =
        view.javaClass.name.endsWith("HwOperatorIconParentView")

    /** Whether any slot-based suppression is switched on at all. */
    private fun wantsSlotSuppression(a: StatusAppearance): Boolean =
        a.hideAlarmIcon || a.hideBluetoothIcon || a.hideHdVolte || a.hideHdVowifi

    /**
     * Whether [view] could carry a slot this module suppresses, judged without reflection.
     *
     * A name check per call is all the cost this leaves on the `setFlags` path; reading the
     * slot itself is a cached-but-reflective field access under a lock.
     */
    private fun isSlotKind(view: View): Boolean {
        val name = view.javaClass.name
        return name.endsWith(STATUS_ICON_VIEW_SUFFIX) || name.endsWith("HwOperatorIconParentView")
    }

    fun settleSlotIcon(view: View, a: StatusAppearance) {
        if (!wantsSlotSuppression(a)) {
            restoreSlotIcon(view)
            return
        }
        if (!hidesSlot(view, a)) {
            restoreSlotIcon(view)
            return
        }
        if (!isSlotTracked(view)) rememberSlotHidden(view, view.visibility)
        rememberHidden(view)
        hide(view)
    }

    /**
     * Hands [view] back with the visibility it had before this module touched it.
     *
     * Deliberately not plain `VISIBLE`: the host hides these icons itself when there is
     * no alarm or no Bluetooth attached, so forcing VISIBLE would draw an icon for a
     * state that is not on. A view the host has shown again since is left alone.
     */
    private fun restoreSlotIcon(view: View) {
        val previous = forgetSlotHidden(view) ?: return
        if (view.isGone) view.visibility = previous
    }

    /** The slot of a `StatusBarIconView`, or null for anything else. */
    private fun slotOf(view: View): String? {
        val cls = view.javaClass
        if (!cls.name.endsWith(STATUS_ICON_VIEW_SUFFIX)) return null
        val f = synchronized(slotFields) {
            slotFields.getOrPut(cls) { Refl.field(cls, "mSlot") }
        } ?: return null
        return Refl.get(f, view) as? String
    }

    private fun isSlotTracked(view: View): Boolean = synchronized(slotHidden) {
        slotHidden.any { it.view.get() === view }
    }

    private fun rememberSlotHidden(view: View, previous: Int) {
        synchronized(slotHidden) {
            slotHidden.add(SlotHide(WeakReference(view), previous))
        }
    }

    /** The visibility to restore, or null when this module did not hide [view]. */
    private fun forgetSlotHidden(view: View): Int? {
        synchronized(slotHidden) {
            for (i in slotHidden.indices.reversed()) {
                val known = slotHidden[i].view.get()
                if (known == null) {
                    slotHidden.removeAt(i)
                } else if (known === view) {
                    return slotHidden.removeAt(i).previous
                }
            }
        }
        return null
    }

    fun enforceSuppression(view: View) {
        val a = ModuleConfig.appearance()
        if (!a.enabled || StatusBarRowManager.suppressed || view.isGone) return
        if (!idsResolved) {
            resolveIds(view.resources)
        }
        val id = view.id
        val knownId = id == wifiId || id == mobileId || id == batteryId || id == clockId
        // Alarm, Bluetooth and the HD badges are not reachable by id: they arrive as a fresh
        // StatusBarIconView whenever the bar is rebuilt, the keyguard included. Their slot
        // read is reflective, and this runs for every `View.setFlags` in SystemUI - so it is
        // asked only when a slot setting is on and only of a view that could be one. The
        // common case stays integer comparisons.
        if (!knownId && wantsSlotSuppression(a) && isSlotKind(view) && hidesSlot(view, a)) {
            if (!isSlotTracked(view)) rememberSlotHidden(view, view.visibility)
            rememberHidden(view)
            hide(view)
            return
        }
        if (id == 0 || !knownId) {
            return
        }
        // The clock is the one icon whose ownership does not depend on the container:
        // its write can land before the view is parented (a re-inflated bar sets
        // visibility during inflate), when the contents walk below has nothing to
        // match yet. The class check keeps the calling and projection clocks - which
        // share the id but are a plain TextView - out of it. A clock in the lock
        // screen's own bar is left alone: the lock screen keeps its own time.
        if (id == clockId &&
            (view is TextView || view.javaClass.name.endsWith(STATUS_BAR_CLOCK_SUFFIX)) &&
            isOwned(id, a) && !isKeyguardClock(view)
        ) {
            rememberHidden(view)
            hide(view)
            return
        }
        if (!insideOwnedContainer(view)) return
        if (isOwned(id, ModuleConfig.appearance())) {
            rememberHidden(view)
            hide(view)
        }
    }

    /**
     * Settles every icon this module does or does not replace.
     *
     * The hook alone cannot do it in either direction: it only sees visibility
     * *writes*, and SystemUI never writes `VISIBLE` to a view that is already `GONE`,
     * nor `GONE` to one already visible. Called from the settings change and from
     * mounting a row.
     */
    fun applyIconVisibility(a: StatusAppearance = ModuleConfig.appearance(), rowsSettled: Boolean = false) {
        // Nothing the module owns may be drawn while it has stood down: no clock
        // text, no re-hiding. Only the hand-back below runs.
        if (StatusBarRowManager.suppressed) {
            BarClockManager.sync(a)
            handBackAll()
            return
        }
        // The master switch is the user saying "give it all back". It is handled before
        // anything else, and without consulting the id table or the layout: with the
        // switch off the module replaces nothing, so every view it hid returns to the
        // visibility the host had given it - the bar's own time and battery included.
        if (!a.enabled) {
            BarClockManager.sync(a)
            settleAllTrackedSlots(a)
            handBackAll()
            return
        }
        if (a.hidesStatusClock()) {
            ClockTicker.refresh()
        }
        BarClockManager.sync(a)
        if (!idsResolved) {
            val res = contentsViewGroup?.resources ?: StatusBarRowManager.firstRowResources()
            resolveIds(res)
        }
        if (!idsResolved) return

        if (!rowsSettled) {
            for (row in StatusBarRowManager.snapshot()) {
                settle(row.container, a)
            }
        }
        contentsViewGroup?.let { settle(it, a) }
        settleAllTrackedSlots(a)

        synchronized(HIDDEN) {
            for (i in HIDDEN.indices.reversed()) {
                val view = HIDDEN[i].view.get()
                if (view == null) {
                    HIDDEN.removeAt(i)
                } else if (!insideOwnedContainer(view)) {
                    // A slot icon is handed back with the visibility the host gave it;
                    // forcing VISIBLE would draw an alarm or Bluetooth icon that is not
                    // actually on. The clock is never handed back on this path either:
                    // its container is the bar's contents, and a re-parented copy (a
                    // keyguard rebuild, a multi-user switch) would otherwise come back
                    // in the corner while the glyph still draws the time.
                    when {
                        // Handed back only when the settings no longer ask for it. A
                        // view the keyguard re-created sits outside every container this
                        // pass walks, and restoring it blindly is what put the alarm
                        // icon back on the lockscreen.
                        isSlotTracked(view) -> if (!hidesSlot(view, a)) restoreSlotIcon(view)
                        view.id == clockId && isOwned(clockId, a) -> hide(view)
                        else -> release(view, a)
                    }
                    HIDDEN.removeAt(i)
                }
            }
        }
    }

    /** Forces [view] hidden when the current appearance says the module owns it. */
    fun rememberHidden(view: View) {
        synchronized(HIDDEN) {
            for (i in HIDDEN.indices.reversed()) {
                val known = HIDDEN[i].view.get()
                if (known == null) {
                    HIDDEN.removeAt(i)
                } else if (known === view) {
                    return
                }
            }
            HIDDEN.add(Hidden(WeakReference(view), view.visibility))
        }
    }

    private fun previousOf(view: View): Int? {
        synchronized(HIDDEN) {
            for (i in HIDDEN.indices.reversed()) {
                val entry = HIDDEN[i]
                val known = entry.view.get()
                if (known == null) {
                    HIDDEN.removeAt(i)
                } else if (known === view) {
                    return entry.previous
                }
            }
        }
        return null
    }

    /** Hands every view this module hid back with the visibility the host gave it. */
    /**
     * Re-settles all tracked slot icon views.
     * Ensures icons like alarm or HD badges update immediately even if unparented or on the lockscreen.
     */
    fun settleAllTrackedSlots(a: StatusAppearance = ModuleConfig.appearance()) {
        synchronized(slotHidden) {
            for (i in slotHidden.indices.reversed()) {
                val v = slotHidden[i].view.get()
                if (v == null) {
                    slotHidden.removeAt(i)
                } else {
                    settleSlotIcon(v, a)
                }
            }
        }
    }

    fun handBackAll() {
        synchronized(HIDDEN) {
            for (entry in HIDDEN) {
                val view = entry.view.get() ?: continue
                if (view.visibility != entry.previous) view.visibility = entry.previous
            }
            HIDDEN.clear()
        }
    }

    /**
     * Hides [view], recording the visibility it had first.
     *
     * Every direct caller - the battery, the type label, the Wi-Fi and mobile icons, the
     * bar's own clock - used to hide without registering, while [release] restores only
     * views it holds a record for. One exclusion (an excluded panel, the lockscreen) then
     * called [handBackAll], which had nothing to hand back, so the native icons stayed
     * gone for good.
     */
    fun hide(view: View) {
        if (view.isGone) return
        rememberHidden(view)
        view.visibility = View.GONE
    }

    fun release(view: View, a: StatusAppearance = ModuleConfig.appearance()) {
        // The lock screen's clock is the system's, never this module's, so a "hide the
        // status clock" setting must not reach it.
        if (view.id == clockId && a.barClockHidden && !isKeyguardClock(view)) {
            if (view.visibility != View.GONE) view.visibility = View.GONE
            return
        }
        // Only ever restore what this module hid. A blind VISIBLE re-showed icons the
        // host hides by itself on the lockscreen, and fighting those writes is what
        // made the bar blink there.
        val previous = previousOf(view) ?: return
        val target = if (view.id == wifiId && !WifiPresence.isConnected) View.GONE else previous
        if (view.visibility != target) view.visibility = target
    }

    /**
     * Puts the bar's own clock where the appearance asks for it.
     *
     * The one place the clock's visibility is decided: the module either draws the
     * time itself and the bar's copy goes, or the native copy comes back. Written as
     * a single rule because "handing back" [release] alone used to force the clock
     * visible on every repaint, and the clock placed inside the glyph flickered back
     * into the top-left corner whenever the suppression hook missed a write.
     *
     * A clock inside the lock screen's own bar is never taken over, whatever the
     * settings say: the lock screen already shows the time in the middle of the screen,
     * and the module's second clock there was both a duplicate and the reason the lock
     * screen's own time disappeared.
     */
    fun settleClock(view: View, a: StatusAppearance = ModuleConfig.appearance()) {
        if (isOwned(clockId, a) && !isKeyguardClock(view)) {
            rememberHidden(view)
            hide(view)
        } else {
            release(view, a)
        }
    }

    /** Whether [view] is a clock in the lock screen's own status bar. */
    private fun isKeyguardClock(view: View): Boolean =
        view.id == clockId && isKeyguardTree(view)

    /**
     * Re-hides the bar's clock when the appearance still owns it.
     *
     * The suppression hook only sees visibility *writes*; a copy re-shown through a
     * path that does not write visibility would stay up. The row asks this on every
     * layout, which is cheap (one id compare and one visibility read) and heals the
     * state within a frame.
     */
    fun reassertClock(a: StatusAppearance = ModuleConfig.appearance()) {
        if (!idsResolved || !isOwned(clockId, a)) return
        val view = BarClockManager.nativeClock ?: return
        if (view.visibility != View.GONE && !isKeyguardClock(view)) {
            rememberHidden(view)
            hide(view)
        }
    }

    /**
     * Whether [view] belongs to a place this module draws into: one of the icon rows,
     * or the bar's contents.
     *
     * The same ids are reused by the calling and projection prompts, whose icons have
     * nothing to do with this glyph; hiding one of those would leave a hole with
     * nothing standing in for it.
     */
    fun insideOwnedContainer(view: View): Boolean {
        val c = contentsViewGroup
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p is ViewGroup && (p === c || StatusBarRowManager.find(p) != null)) {
                return true
            }
            p = (p as? View)?.parent
        }
        return false
    }
}
