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

package io.github.odomu.honorduo.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.View
import androidx.core.content.ContextCompat
import io.github.odomu.honorduo.hook.BatteryPresence
import io.github.odomu.honorduo.hook.ClockTicker
import io.github.odomu.honorduo.hook.FpsTicker
import io.github.odomu.honorduo.hook.Refl
import io.github.odomu.honorduo.hook.TrioHooks
import io.github.odomu.honorduo.hook.WifiPresence
import io.github.odomu.honorduo.model.TrioState.Companion.SIMS_INTERVAL_MS
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of everything the trio glyph needs: battery level and charge state,
 * the Wi-Fi and mobile signal levels, and the ink MagicOS is drawing the status
 * bar icons in.
 *
 * Every reading comes from a public field or method of the host, so nothing
 * here guesses:
 *
 * - battery state from `HwBatteryManager`, the singleton
 *   `HwStatusBarBatteryView.onBatteryUpdate` itself reads, so the glyph and the
 *   native battery artwork can never disagree;
 * - ink from the battery's border view (`BatteryTintView.mDefaultColor`), which is
 *   the colour `HwStatusbarContents.refreshTint` last handed to the native
 *   battery and therefore the colour the user is looking at;
 * - Wi-Fi and mobile levels from the two `setIconForView` entry points, the one
 *   call every signal icon on this ROM goes through;
 * - per-SIM levels from telephony, because the icons name no SIM.
 */
internal class TrioState(val host: View) {

    @JvmField
    var level = -1

    @JvmField
    var charging = false

    @JvmField
    var quickCharging = false

    @JvmField
    var low = false

    @JvmField
    var powerSave = false

    /** The ink this host's glyph is drawn with, resolved in [refresh]. */
    @JvmField
    var ink = 0

    @JvmField
    var wifiLevel = -1

    @JvmField
    var mobileLevel = -1

    @JvmField
    var wifiPresent = true

    @JvmField
    var mobileType = ""

    /** Per-card types by slot, each falling back to [mobileType] when empty. */
    @JvmField
    var slotTypes: Array<String> = arrayOf("", "")

    /** Per-SIM levels by slot; a fresh array each refresh, never mutated in place. */
    @JvmField
    var slotLevels = intArrayOf(-1, -1)

    /** How many of [slotLevels] carry a reading. */
    @JvmField
    var sims = 0

    /**
     * Re-reads every status value from the host. Cheap enough to call in onDraw.
     */
    fun refresh() {
        ensureHostFields(host.javaClass)
        ensureManager(host.javaClass.classLoader)

        val manager = manager()
        if (manager != null && mgrLevel != null) {
            level = Refl.getInt(mgrLevel, manager, level)
            charging = Refl.getBool(mgrCharging, manager, charging)
            powerSave = Refl.getBool(mgrPowerSave, manager, powerSave)
            quickCharging = Refl.getBool(mgrQuick, manager, false) ||
                    Refl.getBool(mgrSuperQuick, manager, false) ||
                    Refl.getBool(mgrTurbo, manager, false)
        } else {
            level = Refl.getInt(hostLevel, host, level)
            charging = Refl.getBool(hostCharging, host, charging)
            powerSave = Refl.getBool(hostPowerSave, host, powerSave)
        }
        low = level >= 0 && level <= TrioConfig.appearance().lowThreshold

        resolvePending(resourcesOf(host))

        // One telephony poll is enough for every host, so it is rate-limited here rather than expressed as a background subscription no host owns.
        if (TrioConfig.get().dualSim || TrioConfig.appearance().drawsAnySimRow() ||
            TrioConfig.appearance().stackedOut() || sMobileLevel < 0
        ) {
            sampleSimsIfDue()
        }

        wifiLevel = sWifiLevel
        mobileLevel = sMobileLevel
        wifiPresent = sWifiPresent
        mobileType = sMobileType
        slotTypes = sSlotTypes.copyOf()

        val slots = sSlotLevels
        slotLevels = intArrayOf(slots[0], slots[1])
        sims = (if (slots[0] >= 0) 1 else 0) + (if (slots[1] >= 0) 1 else 0)

        // Single row, no answer from the icon chain: the row still means "the SIM
        // that carries data", and the sampler resolved exactly which one that is.
        if (mobileLevel < 0 && sDataSlot in 0 until SIM_SLOTS && slots[sDataSlot] >= 0) {
            mobileLevel = slots[sDataSlot]
        }

        ink = resolveInk()
    }

    /**
     * The battery's own `mTintColor` first, the border's `mDefaultColor` second.
     *
     * `HwStatusbarContents.refreshTint()` writes `mTintColor` unconditionally, while the
     * `TintUtil.updateTintWithArea` pass that refreshes the border's tint list runs over
     * the battery's children - and the battery is GONE here. Preferring the border made
     * the glyph keep the previous background's ink across a light/dark switch until the
     * border happened to be repainted.
     */
    private fun resolveInk(): Int {
        val tint = Refl.getInt(hostTint, host, 0)
        if (tint != 0) {
            noteInk(tint, "mTintColor")
            return tint
        }
        val border = Refl.get(hostBorder, host)
        if (border != null) {
            if (borderInk == null) borderInk = Refl.field(border.javaClass, "mDefaultColor")
            val colour = Refl.getInt(borderInk, border, 0)
            if (colour != 0) {
                noteInk(colour, "border")
                return colour
            }
        }
        val fallback = if (nightMode()) WHITE else DEFAULT_FOREGROUND
        noteInk(fallback, "fallback")
        return fallback
    }

    /** Records the resolved ink once per change, so a device log can be read off. */
    private fun noteInk(resolved: Int, source: String) {
        if (!TrioHooks.debugLog()) return
        val note = "ink: $source -> ${Integer.toHexString(resolved)}"
        if (note == sLastInk) return
        sLastInk = note
        TrioHooks.log(TrioHooks.LOG_INFO, note)
    }

    /** The plain icon colour: what MagicOS would paint the battery icon in. */
    fun foreground(): Int = if (ink != 0) ink else DEFAULT_FOREGROUND

    private fun nightMode(): Boolean {
        val context = appContext() ?: return false
        return try {
            val uiMode = context.resources.configuration.uiMode
            uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        } catch (_: Throwable) {
            false
        }
    }

    companion object {

        /** Latest resolved signal levels; shared by every trio host. */
        @Volatile
        @JvmField
        var sWifiLevel = -1

        @Volatile
        @JvmField
        var sMobileLevel = -1

        /**
         * Whether the status bar is showing a Wi-Fi indicator at all.
         *
         * The level alone is not enough: the icon chain only reports a level
         * while Wi-Fi is connected, so switching Wi-Fi off would leave the last
         * level behind and the arcs would never disappear.
         */
        @Volatile
        @JvmField
        var sWifiPresent = true

        /**
         * Mobile network type as the status bar itself renders it: "5G", "5GA",
         * "4G"... Empty when there is nothing to show.
         */
        @Volatile
        @JvmField
        var sMobileType = ""

        /**
         * Per-card types by slot.
         *
         * The icon hook sees each card's own type icon, and the card it sits in is the
         * slot, so the two can be recorded together - one table, one source, per card
         * rather than one value standing for both.
         */
        @Volatile
        private var sSlotTypes = arrayOf("", "")

        /**
         * The clock string the capsule arrangement draws: "19:41", or "9:41" where
         * the locale's own time format says so.
         *
         * Kept here rather than sampled in the renderer because the renderer
         * also serves the settings preview, which must not tick.
         */
        @Volatile
        @JvmField
        var sClockText = ""

        /** The date the settings preview stacks above its clock sample (preview only). */
        @Volatile
        @JvmField
        var sClockDate = ""

        /** The time the clock sheet's own sample draws; unlike [sClockText] it has seconds. */
        @Volatile
        @JvmField
        var sClockBarText = ""

        
        @Volatile
        @JvmField
        var sClockWeekday = ""

        @Volatile
        @JvmField
        var sClockLunar = ""

        @Volatile
        @JvmField
        var sDataActivity = 0

        /**
         * Joins the date and the weekday into the one line above the time.
         *
         * @return null when neither is on, which keeps the clock a single line.
         */
        @JvmStatic
        fun clockDateLine(date: String?, weekday: String?): String? {
            val d = date.orEmpty()
            val w = weekday.orEmpty()
            return when {
                d.isEmpty() -> w.takeIf { it.isNotEmpty() }
                w.isEmpty() -> d
                else -> "$d $w"
            }
        }

        /**
         * Whether an external power source is attached at all.
         *
         * Not the same question as [charging]: at 100 % the platform stops
         * pushing current, and a readout that vanished there would disappear
         * exactly where a user looks for it.
         */
        @Volatile
        @JvmField
        var sChargingAttached = false

        /**
         * The power going into the battery, in watts, or [POWER_UNKNOWN] until a
         * source answers. Defaults to "unknown" rather than 0: a module that never
         * managed to read the current must not print "0.0W" as if it had.
         */
        @Volatile
        @JvmField
        var sChargePowerW = POWER_UNKNOWN

        /** No temperature reading has arrived yet; draw nothing rather than "0". */
        const val TEMP_UNKNOWN = Int.MIN_VALUE

        /**
         * The platform would not say how much power is going in; draw nothing
         * rather than "0.0W". A NaN so that a comparison against it reads as "is a
         * reading".
         */
        const val POWER_UNKNOWN = Float.NaN

        /** The battery temperature in whole degrees Celsius, or [TEMP_UNKNOWN]. */
        @Volatile
        @JvmField
        var sBatteryTempC = TEMP_UNKNOWN

        /** The display's current refresh rate in whole hertz, or 0 before it is read. */
        @Volatile
        @JvmField
        var sFps = 0

        /** How many SIM slots the reading covers. */
        const val SIM_SLOTS = 2

        /** Signal level per slot, or -1 where that slot has no reading. */
        @Volatile
        @JvmField
        var sSlotLevels = intArrayOf(-1, -1)

        /** The default-data SIM's slot, or -1 when unknown. */
        @Volatile
        @JvmField
        var sDataSlot = -1

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DEFAULT_FOREGROUND = WHITE

        private const val SIMS_INTERVAL_MS = 2_000L
        private const val PENDING_MAX = 8

        private const val C_IGNORE = -1
        private const val C_CLEAR = -2
        private const val C_TYPE = -3
        private const val C_WIFI_BASE = 100

        private val PENDING = ArrayList<Int>()
        private val CLASSIFIED = ConcurrentHashMap<Int, Int>()

        /**
         * The application context, captured from the first hooked view.
         *
         * Held weakly on purpose: this object is a process-wide singleton inside
         * SystemUI, and a strong `Context` field there is the leak the platform's own
         * lint warns about. The application is alive for the whole process regardless,
         * so the reference never actually clears in practice.
         */
        @Volatile
        private var sContextRef: java.lang.ref.WeakReference<Context>? = null

        private fun appContext(): Context? = sContextRef?.get()

        @Volatile
        private var sLastInk = ""

        @Volatile
        private var sSimsDueAt = 0L

        // Battery manager (com.android.systemui.battery.HwBatteryManager).
        private var mgrGetInstance: java.lang.reflect.Method? = null
        private var mgrLevel: Field? = null
        private var mgrCharging: Field? = null
        private var mgrPowerSave: Field? = null
        private var mgrQuick: Field? = null
        private var mgrSuperQuick: Field? = null
        private var mgrTurbo: Field? = null
        private var mgrTried = false

        // Host fields, used when the singleton cannot be reached.
        private var hostLevel: Field? = null
        private var hostCharging: Field? = null
        private var hostPowerSave: Field? = null
        private var hostTint: Field? = null
        private var hostBorder: Field? = null
        private var borderInk: Field? = null
        private var hostFieldsReady = false

        /**
         * Captures the context telephony needs, from a view the module already has.
         *
         * SystemUI is `android.uid.systemui` with READ_PRIVILEGED_PHONE_STATE,
         * and this module runs inside it, so the framework answers these calls
         * without the module's own manifest asking for anything.
         */
        @JvmStatic
        fun attachContext(context: Context?) {
            if (context == null || sContextRef != null) return
            try {
                val app = context.applicationContext ?: context
                sContextRef = java.lang.ref.WeakReference(app)
                WifiPresence.start(app)
                // The battery broadcast is not routed through the hooked views, so
                // it needs a receiver of its own.
                BatteryPresence.start(app)
                // The one reading with no event behind it: the clock is correct
                // until the minute rolls over, so it has to wake itself up.
                ClockTicker.start(app)
                // The one reading with no event behind it either: the panel's refresh rate
                // moves on its own schedule, so it is sampled rather than pushed. The
                // ticker reports through this callback, so it never has to know what a new
                // rate repaints.
                FpsTicker.onSample = { fps ->
                    sFps = fps
                    TrioHooks.invalidateHosts()
                }
                FpsTicker.start(app)
                startDataActivityListener(app)
                startDataActivitySampler(app)
            } catch (_: Throwable) {
                // Leave it null; the reading stays on the single-row fallback.
            }
        }

        /**
         * Records a signal icon resource id - the id every signal icon on this ROM
         * is applied through.
         *
         * Names look like `stat_sys_signal_five_3_small` (the five-bar layout),
         * `stat_sys_signal_3` (four-bar), `stat_sys_wifi_signal_2` or
         * `stat_sys_data_connected_5g` (the network type). Ids seen before a
         * Resources handle exists are buffered and resolved on the next draw.
         */
        @JvmStatic
        fun noteSignalIcon(res: Resources?, resId: Int) {
            if (resId == 0) return
            if (res == null) {
                synchronized(PENDING) {
                    if (PENDING.size >= PENDING_MAX) PENDING.removeAt(0)
                    PENDING.add(resId)
                }
                return
            }
            absorb(res, resId)
        }

        /** Records Wi-Fi presence and reports whether it changed. */
        @JvmStatic
        fun setWifiPresent(present: Boolean): Boolean {
            val resolved = WifiPresence.resolve(present, -1)
            if (sWifiPresent == resolved) return false
            sWifiPresent = resolved
            return true
        }

        /**
         * Records one card's type. Empty clears it.
         *
         * @param fromIcon `true` when the label was read off the card's own type icon,
         *   `false` when telephony reported it. Telephony decides the family and the
         *   icon may only refine it: Honor draws `stat_sys_data_connected_5g_cust_available`
         *   on an LTE cell whose carrier merely advertises NR as available, and taking
         *   that name at face value claimed a 5G network the card was not on. An icon
         *   whose family agrees with the radio still wins, which is what keeps "5GA"
         *   and "4G+" - refinements the radio has no word for.
         */
        @JvmStatic
        fun setSlotType(slot: Int, type: String?, fromIcon: Boolean = true) {
            if (slot !in 0 until SIM_SLOTS) return
            val next = type ?: ""
            if (fromIcon) {
                val radio = sRadioTypes[slot]
                if (radio.isNotEmpty() && !next.startsWith(radio)) return
            } else {
                val radios = sRadioTypes.copyOf()
                radios[slot] = next
                sRadioTypes = radios
            }
            if (sSlotTypes[slot] == next) return
            val copy = sSlotTypes.copyOf()
            copy[slot] = next
            sSlotTypes = copy
        }

        /**
         * What telephony last said about each card's family.
         *
         * Kept beside the published labels so the icon path can be checked against it:
         * the icon is the refinement, the radio is the answer.
         */
        private var sRadioTypes = arrayOf("", "")

        /** The type an icon id names, or null when it names none. Cached per id. */
        @JvmStatic
        fun typeOfIcon(res: Resources?, resId: Int): String? {
            if (res == null || resId <= 0) return null
            synchronized(TYPE_NAMES) {
                if (TYPE_NAMES.containsKey(resId)) {
                    return TYPE_NAMES[resId]?.ifEmpty { null }
                }
                val name = runCatching { res.getResourceEntryName(resId) }.getOrNull()
                val type = name?.let { typeOfName(it) }
                TYPE_NAMES[resId] = type ?: ""
                return type?.ifEmpty { null }
            }
        }

        /** Resource ids already classified as a type label; "" is "not a type". */
        private val TYPE_NAMES = HashMap<Int, String>()

        /** Records the network type label and reports whether it changed. */
        @JvmStatic
        fun setMobileType(type: String?): Boolean {
            val next = type ?: ""
            if (next == sMobileType) return false
            sMobileType = next
            return true
        }

        /**
         * Re-reads the per-SIM levels right away and reports whether anything moved.
         *
         * Called from the signal-icon hook, which is an event-driven refresh
         * rather than a per-frame one, so the frame-rate guard is not what protects
         * that path.
         */
        @JvmStatic
        fun pollSimsNow(): Boolean {
            val context = appContext() ?: return false
            val before = sSlotLevels
            val beforeData = sDataSlot
            sampleSims(context)
            val after = sSlotLevels
            return beforeData != sDataSlot || before[0] != after[0] || before[1] != after[1]
        }

        private fun ensureManager(cl: ClassLoader?) {
            if (mgrTried) return
            mgrTried = true
            val c =
                Refl.cls("com.android.systemui.battery.HwBatteryManager", cl ?: return) ?: return
            mgrGetInstance = Refl.method(c, "getInstance")
            mgrLevel = Refl.field(c, "mLevel")
            mgrCharging = Refl.field(c, "mIsCharging")
            mgrPowerSave = Refl.field(c, "mIsPowerSave")
            mgrQuick = Refl.field(c, "mIsQuickStatus")
            mgrSuperQuick = Refl.field(c, "mIsSuperQuickStatus")
            mgrTurbo = Refl.field(c, "mIsTurboCharging")
        }

        /**
         * The battery-manager singleton.
         *
         * `HwBatteryManager.getInstance()` is the object
         * `HwStatusBarBatteryView.onBatteryUpdate` reads its state from, so a glyph
         * fed from the same object cannot show a level the native battery
         * disagrees with.
         */
        private fun manager(): Any? = Refl.invoke(mgrGetInstance, Unit)

        private fun ensureHostFields(c: Class<*>) {
            if (hostFieldsReady) return
            hostLevel = Refl.field(c, "mLevel")
            hostCharging = Refl.field(c, "mIsCharging")
            hostPowerSave = Refl.field(c, "mIsPowerSave")
            hostTint = Refl.field(c, "mTintColor")
            hostBorder = Refl.field(c, "mBorderView")
            hostFieldsReady = hostLevel != null
        }

        private fun resourcesOf(v: View): Resources? = try {
            v.resources
        } catch (_: Throwable) {
            null
        }

        /**
         * Re-reads the per-SIM levels at most once every [SIMS_INTERVAL_MS].
         *
         * Called from [refresh], which runs in onDraw: the guard is what keeps a
         * per-frame call from becoming a per-frame IPC.
         */
        private fun sampleSimsIfDue() {
            val now = SystemClock.elapsedRealtime()
            if (now < sSimsDueAt) return
            sSimsDueAt = now + SIMS_INTERVAL_MS
            sampleSims(appContext())
        }

        /**
         * Reads one signal level per SIM slot straight from telephony.
         *
         * Why not the icons: `setIconForView` names neither a SIM nor a slot, so
         * the icons cannot say which SIM a level belongs to. The framework can, and
         * [SubscriptionInfo] is the one place that maps a subscription to its slot.
         *
         * Every step is defensive: a missing permission or a dead radio leaves that slot
         * at -1 and the caller falls back to a single row.
         */
        private fun sampleSims(source: Context?) {
            val context = source ?: return
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val levels = intArrayOf(-1, -1)
            var dataSlot = -1
            try {
                val subs = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as? SubscriptionManager
                val tel = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (subs == null || tel == null) return
                val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
                subs.activeSubscriptionInfoList?.forEach { info ->
                    val subId = info.subscriptionId
                    val slot = info.simSlotIndex
                    if (subId == dataSubId) dataSlot = slot
                    if (slot in 0 until SIM_SLOTS) {
                        levels[slot] = levelOf(tel, subId)
                        // The radio decides the family; the icon only refines it, and is replaced when stale.
                        // The ROM's own per-slot answer first: it already folded in the
                        // NSA override, and it is the value its icon was chosen from.
                        val radio = romSlotType(slot, subId) ?: typeOf(tel, subId)
                        if (radio != null) {
                            setSlotType(slot, radio, fromIcon = false)
                        }
                    }
                }
                // The aggregate reading comes from the data subscription rather than from
                // the icon that happens to carry it.
                if (dataSlot in 0 until SIM_SLOTS && levels[dataSlot] >= 0) {
                    sMobileLevel = levels[dataSlot]
                }
            } catch (_: Throwable) {
                // keep whatever the previous poll found.
            }
            // Published only once the whole reading is consistent, so a frame never
            // pairs SIM 1's level with SIM 2's from a different poll.
            sSlotLevels = levels
            sDataSlot = dataSlot
        }

        /**
         * The 0..4 level the bar draws for one subscription, or -1.
         *
         * AOSP's `SignalStrength.getLevel()` answers 0..4 on every level count,
         * including MagicOS' five-bar layout, so it is the one reading that matches
         * the dot row regardless of how many bars the ROM draws.
         */
        /**
         * The network type of one subscription, in the module's own label vocabulary.
         *
         * Empty for an unknown or absent radio; the caller keeps whatever the icon
         * reported in that case.
         */
        /**
         * The network type the ROM itself is showing for one slot, or null.
         *
         * `HwSignalUtil.sCurrentNetWorkTypes[slot]` is the table SystemUI fills in
         * `HwMobileSignalController.notifyListeners` from `TelephonyDisplayInfo`, and
         * every per-card type icon is derived from it. Reading the same table means the
         * module cannot disagree with the bar about what a card is on - including the NSA
         * override, which `TelephonyManager.dataNetworkType` does not carry.
         *
         * @return the module's own label, or null when the ROM has not written that slot
         */
        private fun romSlotType(slot: Int, subId: Int): String? {
            val table = romTypeTable() ?: return null
            val perSlot = table.getOrNull(slot) as? Map<*, *> ?: return null
            val raw = perSlot[subId] as? Number ?: return null
            return labelOfNetworkType(raw.toInt())
        }

        /** The ROM's per-slot type table, resolved once. */
        private fun romTypeTable(): Array<*>? {
            if (romTried) return romTypes
            romTried = true
            romTypes = try {
                val loader = appContext()?.classLoader ?: return null
                val c = Refl.cls("com.android.systemui.utils.HwSignalUtil", loader)
                Refl.field(c, "sCurrentNetWorkTypes")?.get(null) as? Array<*>
            } catch (_: Throwable) {
                null
            }
            return romTypes
        }

        private var romTried = false
        private var romTypes: Array<*>? = null

        /**
         * The module's label for a raw `TelephonyManager` network type.
         *
         * The same families the status bar's own icons use: 20 is NR and is also what
         * the ROM stores for an NSA override, 19 is LTE carrier aggregation, which Honor
         * draws as "4G+".
         */
        private fun labelOfNetworkType(type: Int): String? = when (type) {
            20 -> "5G"
            19 -> "4G+"
            13, 14 -> "4G"
            3, 5, 6, 8, 9, 10, 12, 15, 17 -> "3G"
            1, 2, 4, 7, 11, 16 -> "2G"
            else -> null
        }

        private fun typeOf(tel: TelephonyManager, subId: Int): String? = try {
            when (tel.createForSubscriptionId(subId)?.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS,
                -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_GSM,
                -> "2G"
                else -> null
            }
        } catch (_: SecurityException) {
            // READ_PHONE_STATE can be revoked while the process lives; the icon-borne
            // label (when there is one) is still on screen, so fall through to it.
            null
        } catch (_: Throwable) {
            null
        }

        private fun levelOf(tel: TelephonyManager, subId: Int): Int = try {
            tel.createForSubscriptionId(subId)?.signalStrength?.level?.takeIf { it in 0..4 } ?: -1
        } catch (_: Throwable) {
            -1
        }

        private var sDataActivityStarted = false
        private var sDataActivitySampler = false

        /**
         * Polls the modem's data-activity counter.
         *
         * The telephony callback is the precise source, but on this ROM it is delivered
         * to the default subscription only and silently stops when the data SIM changes
         * or a privileged listener is refused - which showed up as arrows that never lit.
         * This one-second poll is the fallback: cheap, and it keeps the state honest even
         * when the callback never arrives.
         */
        private fun startDataActivitySampler(context: Context) {
            if (sDataActivitySampler) return
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return
            sDataActivitySampler = true
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val tick = object : Runnable {
                override fun run() {
                    try {
                        val method = tm.javaClass.getMethod("getDataActivity")
                        val direction = (method.invoke(tm) as? Number)?.toInt() ?: 0
                        if (sDataActivity != direction) {
                            sDataActivity = direction
                            TrioHooks.invalidateHosts()
                        }
                    } catch (_: Throwable) {
                        // Hidden on some builds; the callback above still covers those.
                    }
                    main.postDelayed(this, DATA_ACTIVITY_POLL_MS)
                }
            }
            main.postDelayed(tick, DATA_ACTIVITY_POLL_MS)
        }

        private const val DATA_ACTIVITY_POLL_MS = 1000L

        private fun startDataActivityListener(context: Context) {
            if (sDataActivityStarted) return
            try {
                val tm = context.getSystemService(TelephonyManager::class.java) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val executor = context.mainExecutor
                    tm.registerTelephonyCallback(
                        executor,
                        object : android.telephony.TelephonyCallback(),
                            android.telephony.TelephonyCallback.DataActivityListener {
                            override fun onDataActivity(direction: Int) {
                                if (sDataActivity != direction) {
                                    sDataActivity = direction
                                    TrioHooks.invalidateHosts()
                                }
                            }
                        }
                    )
                    sDataActivityStarted = true
                }
            } catch (_: Throwable) {
            }
        }

        private fun resolvePending(res: Resources?) {
            if (res == null) return
            synchronized(PENDING) {
                if (PENDING.isEmpty()) return
                PENDING.forEach { absorb(res, it) }
                PENDING.clear()
            }
        }

        private fun absorb(res: Resources, resId: Int) {
            // Classification is a pure function of the resource id, and this runs on
            // the hot icon path, so it is cached.
            val code = CLASSIFIED[resId] ?: classify(res, resId).also { CLASSIFIED[resId] = it }
            when {
                code == C_IGNORE || code == C_TYPE -> Unit
                code == C_CLEAR -> sMobileLevel = 0
                code >= C_WIFI_BASE -> sWifiLevel = code - C_WIFI_BASE
                else -> sMobileLevel = code
            }
        }

        private fun classify(res: Resources, resId: Int): Int {
            val code = computeCode(res, resId)
            CLASSIFIED[resId] = code
            return code
        }

        private fun computeCode(res: Resources, resId: Int): Int {
            val name = try {
                res.getResourceEntryName(resId)
            } catch (_: Throwable) {
                return C_IGNORE
            } ?: return C_IGNORE
            if (name.contains("activity_inout")) {
                sDataActivity = 3
            } else if (name.contains("activity_in")) {
                sDataActivity = 1
            } else if (name.contains("activity_out")) {
                sDataActivity = 2
            }

            // A network-type icon is not a level, but it is the reading the glyph draws when it owns the label - so it is sampled here rather than from a second hook.
            typeOfName(name)?.let { type ->
                setMobileType(type)
                return C_TYPE
            }

            val wifi = name.startsWith("stat_sys_wifi_signal") ||
                    (name.startsWith("stat_sys_wifi2") && name.contains("_signal_"))
            if (!wifi && !name.startsWith("stat_sys_signal")) return C_IGNORE

            val value = parseLevel(name)
            if (value < 0) {
                // Decoration rather than a level change: keep the last level.
                return if (name == "stat_sys_signal_null" || name == "stat_sys_signal_flightmode") {
                    C_CLEAR
                } else {
                    C_IGNORE
                }
            }
            return if (wifi) C_WIFI_BASE + value else value
        }

        /**
         * The level an icon name carries, normalised to 0..4.
         *
         * MagicOS has two families on this ROM: `stat_sys_signal_N` (four bars) and
         * `stat_sys_signal_five_N[_small|_fully]` (five bars, the shape the stock bar
         * draws on a 5G phone). The glyph has four dots, so a five-bar level is scaled
         * rather than truncated - a full five bars must still read as full.
         */
        private fun parseLevel(name: String): Int {
            val parts = name.split("_")
            for (i in 0 until parts.size - 1) {
                if (parts[i] != "signal") continue
                val five = parts[i + 1] == "five"
                val raw = when {
                    five -> parts.getOrNull(i + 2)
                    else -> parts[i + 1]
                }
                if (raw == null || raw.length != 1 || raw[0] !in '0'..'9') return -1
                val max = if (five) 5 else 4
                val value = raw[0] - '0'
                if (value > max) return -1
                return if (max == 4) value else Math.round(value * 4f / max)
            }
            return -1
        }

        /**
         * The network type an icon name names, or `null` when it names none.
         *
         * MagicOS' own `MobileMappings` picks `stat_sys_data_connected_*` for the
         * connected state and `stat_sys_data_fully_connected_*` for the
         * fully-connected one; both families carry the same suffixes, so one table
         * covers them.
         */
        @JvmStatic
        fun typeOfName(name: String): String? {
            if (!name.startsWith("stat_sys_data_")) return null
            if (name.contains("disabled")) return ""
            // Roaming is drawn as its own marker; the type behind it is unchanged.
            if (name.contains("roam")) return null
            return when {
                name.contains("_5ga") -> "5GA"
                name.contains("_5point5g") -> "5.5G"
                name.contains("_4point5g") -> "4.5G"
                name.contains("_5g_plus") || name.contains("_5gplus") -> "5G+"
                name.contains("_5g") -> "5G"
                name.contains("_4gplus") || name.contains("_4g_plus") -> "4G+"
                name.contains("_4g") -> "4G"
                name.contains("_3gplus") || name.contains("_3g_plus") -> "3G+"
                name.contains("_3g") -> "3G"
                name.contains("_2g") -> "2G"
                name.contains("_hplus") -> "H+"
                name.endsWith("_h") -> "H"
                name.endsWith("_e") -> "E"
                name.endsWith("_g") || name.contains("_g_for_gsm_gprs") -> "G"
                name.contains("_1x") || name.contains("_cdma") -> "1X"
                else -> null
            }
        }
    }
}
