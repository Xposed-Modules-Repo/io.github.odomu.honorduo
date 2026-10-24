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

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.core.view.isGone
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioConfig
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

    /** The bar's contents row, which holds the clock; registered when it attaches. */
    @Volatile
    var contents: WeakReference<ViewGroup>? = null

    val contentsViewGroup: ViewGroup?
        get() = contents?.get()

    /** Views hidden by this module, so a detached row cannot leave one behind. */
    private val HIDDEN = ArrayList<WeakReference<View>>()


    /** Views hidden because of their slot, with the visibility they had before. */
    private val slotHidden = ArrayList<SlotHide>()

    private class SlotHide(val view: WeakReference<View>, val previous: Int)

    /** `mSlot` per icon view class; one entry in practice. */
    private val slotFields = HashMap<Class<*>, Field?>(2)

    private const val STATUS_ICON_VIEW_SUFFIX = "StatusBarIconView"

    /** The stylus icon the bar also draws from the Bluetooth slot family. */
    private const val BLUETOOTH_PENCIL_SLOT = "bluetooth_pencil"

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
        return try {
            val key = res.getIdentifier(name, "string", "android")
            if (key != 0) res.getString(key) else ""
        } catch (ignored: Throwable) {
            ""
        }
    }

    private fun id(res: Resources, name: String): Int {
        return try {
            res.getIdentifier(name, "id", "com.android.systemui")
        } catch (ignored: Throwable) {
            0
        }
    }

    /**
     * The one ownership rule: which status-bar icon this module replaces right now.
     *
     * The visibility hook, the settings change and mounting a row all ask this and
     * nothing else, so hiding an icon and handing it back cannot disagree.
     */
    fun isOwned(id: Int, a: TrioAppearance): Boolean = when {
        !a.enabled -> false
        id == wifiId -> a.glyph && a.ownsWifi()
        id == mobileId -> (a.glyph && (a.signalDots() || a.drawsAnySimRow())) || a.stackedOut()
        id == batteryId -> a.glyph
        id == clockId -> a.barClockHidden || (a.glyph && a.hidesStatusClock()) || BarClockManager.isClockAttached
        else -> false
    }

    /** Walks [root] and puts every icon in it into the state [isOwned] asks for. */
    fun settle(root: View, a: TrioAppearance) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                settle(root.getChildAt(i), a)
            }
        }
        val id = root.id
        if (id == wifiId || id == mobileId || id == batteryId || id == clockId) {
            if (isOwned(id, a)) {
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
    private fun settleSlotIcon(view: View, a: TrioAppearance) {
        if (!a.hideAlarmIcon && !a.hideBluetoothIcon) {
            restoreSlotIcon(view)
            return
        }
        val slot = slotOf(view) ?: return
        val hide = (a.hideAlarmIcon && alarmSlot.isNotEmpty() && slot == alarmSlot) ||
            (a.hideBluetoothIcon && (slot == bluetoothSlot || slot == BLUETOOTH_PENCIL_SLOT))
        if (hide) {
            if (!isSlotTracked(view)) rememberSlotHidden(view, view.visibility)
            rememberHidden(view)
            hide(view)
        } else {
            restoreSlotIcon(view)
        }
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
        if (view.visibility == View.GONE) view.visibility = previous
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
        if (view.isGone) return
        if (!idsResolved) {
            resolveIds(view.resources)
        }
        val id = view.id
        if (id == 0 || (id != wifiId && id != mobileId && id != batteryId && id != clockId)) {
            return
        }
        if (!insideOwnedContainer(view)) return
        if (isOwned(id, TrioConfig.appearance())) {
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
    fun applyIconVisibility(a: TrioAppearance = TrioConfig.appearance()) {
        if (a.hidesStatusClock()) {
            ClockTicker.refresh()
        }
        BarClockManager.sync(a)
        if (!idsResolved) {
            val res = contentsViewGroup?.resources ?: StatusBarRowManager.firstRowResources()
            resolveIds(res)
        }
        if (!idsResolved) return

        for (row in StatusBarRowManager.snapshot()) {
            settle(row.container, a)
        }
        contentsViewGroup?.let { settle(it, a) }

        synchronized(HIDDEN) {
            for (i in HIDDEN.indices.reversed()) {
                val view = HIDDEN[i].get()
                if (view == null) {
                    HIDDEN.removeAt(i)
                } else if (!insideOwnedContainer(view)) {
                    // A slot icon is handed back with the visibility the host gave it;
                    // forcing VISIBLE would draw an alarm or Bluetooth icon that is not
                    // actually on.
                    if (isSlotTracked(view)) restoreSlotIcon(view) else release(view, a)
                    HIDDEN.removeAt(i)
                }
            }
        }
    }

    /** Forces [view] hidden when the current appearance says the module owns it. */
    fun rememberHidden(view: View) {
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

    fun hide(view: View) {
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
        }
    }

    fun release(view: View, a: TrioAppearance = TrioConfig.appearance()) {
        val target = when (view.id) {
            wifiId -> if (WifiPresence.isConnected) View.VISIBLE else View.GONE
            clockId -> if (a.barClockHidden) View.GONE else View.VISIBLE
            else -> View.VISIBLE
        }
        if (view.visibility != target) {
            view.visibility = target
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
