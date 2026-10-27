package io.github.odomu.honorduo.hook.core

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.statusbar.BarClockManager
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconManager
import io.github.odomu.honorduo.hook.statusbar.StatusBarRowManager
import io.github.odomu.honorduo.hook.tracker.BarVisibility
import io.github.odomu.honorduo.hook.tracker.ScopeTracker
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.render.IconRenderer
import java.lang.ref.WeakReference

/**
 * Manages low-level hooks on MagicOS SystemUI status-bar host views and containers.
 *
 * Responsibilities:
 * - Intercepts `HwSystemIcons` lifecycle (inflation, layout passes, capsule avoidance, detaching).
 * - Intercepts `HwStatusBarBatteryView` updates and lifecycle to coordinate glyph mounting.
 * - Suppresses native icons via `View.setFlags` visibility enforcement.
 * - Hooks `HwStatusbarContents` and `HwStatusBarClock` to extract system typeface and clock views.
 * - Listens for tint changes across status-bar containers to refresh glyph and clock colors.
 */
internal object SystemBarHook {

    /**
     * Installs all status bar view and container hooks into the target ClassLoader.
     *
     * @param module Active Xposed module instance used to register hooks.
     * @param cl SystemUI ClassLoader containing target view classes.
     * @return Total count of successfully installed hooks.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun install(module: XposedModule, cl: ClassLoader): Int {
        var hooked = 0
        hooked += hookRow(module, cl)
        hooked += hookBattery(module, cl)
        hooked += hookVisibility(module)
        hooked += hookContents(module, cl)
        hooked += hookClockFace(module, cl)
        hooked += hookTint(module, cl)
        hooked += hookPanel(module, cl)
        hooked += hookBluetooth(module, cl)
        hooked += hookOperatorIcons(module, cl)
        return hooked
    }
    /**
     * Hooks `HwSystemIcons` container methods to manage module view lifecycle and capsule collision.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun hookRow(module: XposedModule, cl: ClassLoader): Int {
        val rowClass = Refl.cls("com.android.systemui.statusbar.HwSystemIcons", cl) ?: return 0
        var n = 0

        // Intercept container inflation to register the row with StatusBarRowManager.
        n += SystemUiHook.hook(module, Refl.method(rowClass, "onFinishInflate"), "honorduo-row-inflate") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.register(self)
            }
            result
        }

        // Intercept layout passes to synchronize custom components and check capsule encroachment.
        n += SystemUiHook.hook(
            module, Refl.method(
                rowClass, "onLayout",
                Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ),
            "honorduo-row-layout",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                val row = StatusBarRowManager.find(self)
                if (row != null) {
                    StatusBarRowManager.requestSync(row)
                }
                StatusBarRowManager.checkCapsuleAvoidance(self)
            }
            result
        }

        // Intercept icon alpha updates triggered by SystemUI animation controllers.
        n += SystemUiHook.hook(module, Refl.methodNamed(rowClass, "updateIconsAlpha", 0), "honorduo-update-icons-alpha") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.checkCapsuleAvoidance(self)
            }
            result
        }

        // Intercept dynamic capsule area notification boundaries (adaptive 5 or 6 arg overload).
        val capsuleMethod = Refl.methodNamed(rowClass, "setCapsuleArea", 6)
            ?: Refl.methodNamed(rowClass, "setCapsuleArea", 5)
        if (capsuleMethod != null) {
            n += SystemUiHook.hook(module, capsuleMethod, "honorduo-capsule-area") { chain ->
                val result = chain.proceed()
                val self = chain.thisObject
                if (self is ViewGroup) {
                    val isShown = chain.getArg(1) as? Boolean ?: false
                    val right = (chain.getArg(2) as? Number)?.toInt() ?: 0
                    StatusBarRowManager.handleCapsuleAvoidance(self, isShown, right)
                }
                result
            }
        }
        val coloredCapsuleMethod = Refl.methodNamed(rowClass, "setColoredCapsuleArea", 3)
        if (coloredCapsuleMethod != null) {
            n += SystemUiHook.hook(module, coloredCapsuleMethod, "honorduo-colored-capsule-area") { chain ->
                val result = chain.proceed()
                val self = chain.thisObject
                if (self is ViewGroup) {
                    val right = (chain.getArg(1) as? Number)?.toInt() ?: 0
                    val isShown = chain.getArg(2) as? Boolean ?: false
                    StatusBarRowManager.handleCapsuleAvoidance(self, isShown, right)
                }
                result
            }
        }

        // Intercept window attachment to track live container instances.
        n += SystemUiHook.hook(module, Refl.method(rowClass, "onAttachedToWindow"), "honorduo-row-attach") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.register(self)
            }
            result
        }

        // Intercept window detachment to clean up registered rows and prevent leaks.
        n += SystemUiHook.hook(module, Refl.method(rowClass, "onDetachedFromWindow"), "honorduo-row-detach") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.drop(self)
            }
            result
        }

        return n
    }

    /**
     * Hooks native battery view updates to sync battery state and hide the stock icon when requested.
     */
    private fun hookBattery(module: XposedModule, cl: ClassLoader): Int {
        val battery = Refl.cls("com.android.systemui.battery.HwStatusBarBatteryView", cl) ?: return 0
        var n = 0

        // Intercept stock battery update events to sync glyph state and hide stock icon.
        n += SystemUiHook.hook(module, Refl.method(battery, "onBatteryUpdate"), "honorduo-battery-update") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                if (ModuleConfig.appearance().glyph) {
                    StatusBarIconManager.hide(self)
                }
                val row = StatusBarRowManager.rowOfBattery(self)
                // While the bar is hidden the update is recorded, not painted: the
                // measured text is rebuilt by the replay when the bar returns.
                if (row != null && BarVisibility.shown) {
                    StatusBarRowManager.syncRow(row, false)
                }
                SystemUiHook.invalidateHosts()
            }
            result
        }

        // Intercept battery view inflation to obtain a valid Context and start screen state tracking.
        n += SystemUiHook.hook(module, Refl.method(battery, "onFinishInflate"), "honorduo-battery-inflate") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                StatusState.attachContext(self.context)
                ModuleConfig.installReceiver(self.context)
                SystemUiHook.ensureScreenStateTracker(self.context)
                ScopeTracker.start(self.context)
            }
            result
        }

        return n
    }

    /**
     * Enforces visibility suppression on stock icons via View.setFlags interception.
     */
    private fun hookVisibility(module: XposedModule): Int {
        val method = Refl.method(
            View::class.java, "setFlags",
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
        ) ?: return 0
        return SystemUiHook.hook(module, method, "honorduo-visibility") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                StatusBarIconManager.enforceSuppression(self)
            }
            result
        }
    }

    /**
     * Hooks `HwStatusbarContents` window attachment to monitor parent status-bar container.
     */
    private fun hookContents(module: XposedModule, cl: ClassLoader): Int {
        val contents = Refl.cls("com.android.systemui.statusbar.HwStatusbarContents", cl) ?: return 0
        return SystemUiHook.hook(module, Refl.method(contents, "onAttachedToWindow"), "honorduo-contents-attach") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarIconManager.contents = WeakReference(self)
                val clockId = StatusBarIconManager.clockId
                if (clockId != 0 && BarClockManager.nativeClock == null) {
                    val native = self.findViewById<View>(clockId) as? TextView
                    if (native != null) {
                        BarClockManager.noteNativeClock(native)
                        StatusBarIconManager.settleClock(native)
                    }
                }
                StatusBarIconManager.applyIconVisibility(ModuleConfig.appearance())
            }
            result
        }
    }

    /**
     * Hooks `HwStatusBarClock` window attachment to extract system typeface and hand over clock view.
     */
    private fun hookClockFace(module: XposedModule, cl: ClassLoader): Int {
        val candidates = arrayOf(
            "com.android.systemui.time.HwStatusBarClock",
            "com.android.systemui.time.HnStatusBarClock",
            "com.android.systemui.statusbar.phone.HnStatusBarClock",
            "com.android.systemui.statusbar.views.HwStatusBarClock",
            "com.android.systemui.statusbar.policy.Clock",
        )
        val clock = candidates.firstNotNullOfOrNull { Refl.cls(it, cl) } ?: return 0
        return SystemUiHook.hook(
            module, Refl.method(clock, "onAttachedToWindow"), "honorduo-clock-face",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is TextView) {
                if (self.typeface != null) {
                    IconRenderer.adoptSystemTypeface(self.typeface)
                    SystemUiHook.invalidateHosts()
                }
                BarClockManager.noteNativeClock(self)
                BarClockManager.dropNow()
                BarClockManager.sync(ModuleConfig.appearance())
            }
            result
        }
    }

    /**
     * Hooks tint refresh calls to invalidate custom components when system switches dark/light mode.
     */
    private fun hookTint(module: XposedModule, cl: ClassLoader): Int {
        var hooked = 0
        val contents = Refl.cls("com.android.systemui.statusbar.HwStatusbarContents", cl)
        if (contents != null) {
            val refreshMethod = Refl.method(contents, "refreshTint")
            if (refreshMethod != null) {
                hooked += SystemUiHook.hook(module, refreshMethod, "honorduo-contents-tint") { chain ->
                    val result = chain.proceed()
                    SystemUiHook.invalidateHosts()
                    result
                }
            }
        }
        val cluster = Refl.cls("com.android.systemui.statusbar.SignalClusterView", cl)
        if (cluster != null) {
            val method = Refl.method(
                cluster, "onDarkChanged",
                ArrayList::class.java, Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            )
            if (method != null) {
                hooked += SystemUiHook.hook(module, method, "honorduo-tint") { chain ->
                    val result = chain.proceed()
                    SystemUiHook.invalidateHosts()
                    result
                }
            }
        }
        return hooked
    }

    /**
     * Feeds the pull-down panel's showing type to [ScopeTracker].
     *
     * `setPanelShowingType` runs on transitions only - 1 shade, 2 control center,
     * 0 collapsed, 3/4 the same two over the lockscreen - so this replaces the old
     * `setExpandedHeightInternal` hook, which did a reflective field read on every
     * frame of every drag. The parameter is a `byte`, which boxes to `Byte`; reading
     * it as `Int` always yielded 0 and reset the state on every call.
     */
    private fun hookPanel(module: XposedModule, cl: ClassLoader): Int {
        val controllerClass = Refl.cls("com.android.systemui.statusbar.phone.HnPanelViewControllerImpl", cl)
            ?: return 0
        val setTypeMethod = Refl.methodNamed(controllerClass, "setPanelShowingType", 1) ?: return 0
        return SystemUiHook.hook(module, setTypeMethod, "honorduo-panel-showing-type") { chain ->
            chain.proceed()
            ScopeTracker.onPanelType((chain.args.firstOrNull() as? Number)?.toInt() ?: 0)
            null
        }
    }

    /**
     * Works out whether the module has to stand down right now and says so.
     *
     * One predicate for the three exclusions, so a panel closing and the lockscreen
     * going away cannot each clear the other's state.
     */
    fun updateEffectiveScopeState() {
        val a = ModuleConfig.appearance()
        val scope = a.effectiveScope
        val excluded = a.enabled && scope != Prefs.SCOPE_ALL && (
            ((scope and Prefs.SCOPE_BIT_CC) != 0 && ScopeTracker.controlCenterActive) ||
                ((scope and Prefs.SCOPE_BIT_SHADE) != 0 && ScopeTracker.shadeActive) ||
                ((scope and Prefs.SCOPE_BIT_KEYGUARD) != 0 && ScopeTracker.lockscreenActive)
            )
        StatusBarRowManager.setSuppressed(excluded)
        if (SystemUiHook.debugLog()) {
            SystemUiHook.log(
                SystemUiHook.LOG_INFO,
                "scope ${ScopeTracker.describe()} mask=$scope -> $excluded",
            )
        }
    }

    /**
     * Hooks Bluetooth status updates to cache headset battery level and invalidate status bar glyphs.
     */
    private fun hookBluetooth(module: XposedModule, cl: ClassLoader): Int {
        val policyClass = Refl.cls("com.android.systemui.statusbar.phone.HwPhoneStatusBarPolicy", cl) ?: return 0
        val updateMethod = Refl.methodNamed(policyClass, "updateBluetooth", 0) ?: return 0
        return SystemUiHook.hook(module, updateMethod, "honorduo-bluetooth-battery") { chain ->
            chain.proceed()
            val policy = chain.thisObject
            if (policy != null) {
                runCatching {
                    val bluetoothField = Refl.field(policy.javaClass, "mBluetooth")
                    val controller = Refl.get(bluetoothField, policy)
                    if (controller != null) {
                        val levelField = Refl.field(controller.javaClass, "mBluetoothBatteryLevel")
                        val level = Refl.getInt(levelField, controller, -1)
                        if (level != StatusState.sBluetoothBatteryLevel) {
                            StatusState.sBluetoothBatteryLevel = level
                            StatusBarRowManager.invalidateAll()
                        }
                    }
                }
            }
            null
        }
    }

    /**
     * Hooks `HwOperatorIconParentView` (MagicOS dual-card HD / VoLTE / VoWiFi badges).
     */
    private fun hookOperatorIcons(module: XposedModule, cl: ClassLoader): Int {
        val operatorClass = Refl.cls("com.android.systemui.operatorname.HwOperatorIconParentView", cl) ?: return 0
        var n = 0

        val attach = Refl.method(operatorClass, "onAttachedToWindow")
        if (attach != null) {
            n += SystemUiHook.hook(module, attach, "honorduo-operator-hd-attach") { chain ->
                val result = chain.proceed()
                val self = chain.thisObject as? View
                if (self != null) {
                    StatusBarIconManager.settleSlotIcon(self, ModuleConfig.appearance())
                }
                result
            }
        }

        val updateMethod = Refl.methodNamed(operatorClass, "updateOperatorIcon", 0)
        if (updateMethod != null) {
            n += SystemUiHook.hook(module, updateMethod, "honorduo-operator-hd-update") { chain ->
                val result = chain.proceed()
                val self = chain.thisObject as? View
                if (self != null) {
                    StatusBarIconManager.settleSlotIcon(self, ModuleConfig.appearance())
                }
                result
            }
        }

        val setCardMethod = Refl.methodNamed(operatorClass, "setCardIcon", 3)
        if (setCardMethod != null) {
            n += SystemUiHook.hook(module, setCardMethod, "honorduo-operator-hd-card") { chain ->
                val result = chain.proceed()
                val self = chain.thisObject as? View
                if (self != null) {
                    StatusBarIconManager.settleSlotIcon(self, ModuleConfig.appearance())
                }
                result
            }
        }

        return n
    }
}
