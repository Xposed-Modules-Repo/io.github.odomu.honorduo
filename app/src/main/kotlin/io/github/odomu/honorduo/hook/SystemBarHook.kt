package io.github.odomu.honorduo.hook

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.TrioConfig
import io.github.odomu.honorduo.model.TrioState
import io.github.odomu.honorduo.render.TrioRenderer
import java.lang.ref.WeakReference
import java.util.ArrayList

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
    fun install(module: XposedModule, cl: ClassLoader): Int {
        var hooked = 0
        hooked += hookRow(module, cl)
        hooked += hookBattery(module, cl)
        hooked += hookVisibility(module)
        hooked += hookContents(module, cl)
        hooked += hookClockFace(module, cl)
        hooked += hookTint(module, cl)
        return hooked
    }

    /**
     * Hooks `HwSystemIcons` container methods to manage module view lifecycle and capsule collision.
     */
    private fun hookRow(module: XposedModule, cl: ClassLoader): Int {
        val rowClass = Refl.cls("com.android.systemui.statusbar.HwSystemIcons", cl) ?: return 0
        var n = 0

        // Intercept container inflation to register the row with StatusBarRowManager.
        n += TrioHooks.hook(module, Refl.method(rowClass, "onFinishInflate"), "honorduo-row-inflate") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.register(self)
            }
            result
        }

        // Intercept layout passes to synchronize custom components and check capsule encroachment.
        n += TrioHooks.hook(
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
        n += TrioHooks.hook(module, TrioHooks.methodNamed(rowClass, "updateIconsAlpha", 0), "honorduo-update-icons-alpha") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.checkCapsuleAvoidance(self)
            }
            result
        }

        // Intercept dynamic capsule area notification boundaries (adaptive 5 or 6 arg overload).
        val capsuleMethod = TrioHooks.methodNamed(rowClass, "setCapsuleArea", 6)
            ?: TrioHooks.methodNamed(rowClass, "setCapsuleArea", 5)
        if (capsuleMethod != null) {
            n += TrioHooks.hook(module, capsuleMethod, "honorduo-capsule-area") { chain ->
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

        // Intercept window attachment to track live container instances.
        n += TrioHooks.hook(module, Refl.method(rowClass, "onAttachedToWindow"), "honorduo-row-attach") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarRowManager.register(self)
            }
            result
        }

        // Intercept window detachment to clean up registered rows and prevent leaks.
        n += TrioHooks.hook(module, Refl.method(rowClass, "onDetachedFromWindow"), "honorduo-row-detach") { chain ->
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
        n += TrioHooks.hook(module, Refl.method(battery, "onBatteryUpdate"), "honorduo-battery-update") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                if (TrioConfig.appearance().glyph) {
                    StatusBarIconManager.hide(self)
                }
                val row = StatusBarRowManager.rowOfBattery(self)
                // While the bar is hidden the update is recorded, not painted: the
                // measured text is rebuilt by the replay when the bar returns.
                if (row != null && BarVisibility.shown) {
                    StatusBarRowManager.syncRow(row, false)
                }
                TrioHooks.invalidateHosts()
            }
            result
        }

        // Intercept battery view inflation to obtain a valid Context and start screen state tracking.
        n += TrioHooks.hook(module, Refl.method(battery, "onFinishInflate"), "honorduo-battery-inflate") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                TrioState.attachContext(self.context)
                TrioConfig.installReceiver(self.context)
                TrioHooks.ensureScreenStateTracker(self.context)
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
        return TrioHooks.hook(module, method, "honorduo-visibility") { chain ->
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
        return TrioHooks.hook(module, Refl.method(contents, "onAttachedToWindow"), "honorduo-contents-attach") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is ViewGroup) {
                StatusBarIconManager.contents = WeakReference(self)
                StatusBarIconManager.applyIconVisibility(TrioConfig.appearance())
            }
            result
        }
    }

    /**
     * Hooks `HwStatusBarClock` window attachment to extract system typeface and hand over clock view.
     */
    private fun hookClockFace(module: XposedModule, cl: ClassLoader): Int {
        val clock = Refl.cls("com.android.systemui.time.HwStatusBarClock", cl) ?: return 0
        return TrioHooks.hook(
            module, Refl.method(clock, "onAttachedToWindow"), "honorduo-clock-face",
        ) { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is TextView) {
                if (self.typeface != null) {
                    TrioRenderer.adoptSystemTypeface(self.typeface)
                    TrioHooks.invalidateHosts()
                }
                BarClockManager.noteNativeClock(self)
                BarClockManager.dropNow()
                BarClockManager.sync(TrioConfig.appearance())
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
                hooked += TrioHooks.hook(module, refreshMethod, "honorduo-contents-tint") { chain ->
                    val result = chain.proceed()
                    TrioHooks.invalidateHosts()
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
                hooked += TrioHooks.hook(module, method, "honorduo-tint") { chain ->
                    val result = chain.proceed()
                    TrioHooks.invalidateHosts()
                    result
                }
            }
        }
        return hooked
    }
}
