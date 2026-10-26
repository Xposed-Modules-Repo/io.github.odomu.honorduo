package io.github.odomu.honorduo.hook.core

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconManager
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.StatusState
import java.lang.reflect.Field
/**
 * Intercepts cellular signal icons and network type indicators from SystemUI.
 *
 * Responsibilities:
 * - Monitors `HnSignalUtils.setIconForView` and `HwSignalUtil.setIconForView` to track signal strength and slot types.
 * - Resolves SIM slot index by traversing parent container hierarchy up to `mobileGroupId`.
 * - Intercepts `SignalUnitNormalView.updateView` to hide native network type text views when requested by configuration.
 */
internal object SignalIconHook {

    /** Reflection field for `mMobileType` in `SignalUnitNormalView`. */
    @Volatile
    private var sUnitTypeField: Field? = null

    /**
     * Installs cellular signal and network type hooks into the target ClassLoader.
     *
     * @param module Active Xposed module instance used to register hooks.
     * @param cl SystemUI ClassLoader containing target view classes.
     * @return Total count of successfully installed hooks.
     */
    fun install(module: XposedModule, cl: ClassLoader): Int {
        var hooked = 0
        hooked += hookSignalIcons(module, cl)
        hooked += hookTypeViews(module, cl)
        return hooked
    }

    /**
     * Hooks native signal utility methods to intercept signal icons and cellular RAT types.
     */
    private fun hookSignalIcons(module: XposedModule, cl: ClassLoader): Int {
        var n = 0
        // Adaptive signal icon utility: prefer modern HnSignalUtils, fallback to classic HwSignalUtil.
        val hn = Refl.cls("com.android.systemui.utils.HnSignalUtils", cl)
        val hnMethod = Refl.methodNamed(hn, "setIconForView", 2)
        if (hnMethod != null) {
            n += StatusHooks.hook(module, hnMethod, "honorduo-signal-new") { chain ->
                val result = chain.proceed()
                val view = chain.getArg(0) as? View
                val id = chain.getArg(1)
                noteIcon(view, id)
                if (view != null && id is Int) {
                    noteSlotType(view, id)
                }
                result
            }
        } else {
            val hw = Refl.cls("com.android.systemui.utils.HwSignalUtil", cl)
            val hwMethod = Refl.methodNamed(hw, "setIconForView", 2)
            if (hwMethod != null) {
                n += StatusHooks.hook(module, hwMethod, "honorduo-signal-classic") { chain ->
                    val result = chain.proceed()
                    noteIcon(chain.getArg(0) as? View, chain.getArg(1))
                    result
                }
            }
        }
        return n
    }

    /**
     * Records the signal icon resource ID and invalidates host views to redraw signal levels.
     */
    private fun noteIcon(view: View?, idObj: Any?) {
        val id = (idObj as? Number)?.toInt() ?: return
        if (id > 0) {
            StatusState.noteSignalIcon(view?.resources, id)
            StatusHooks.invalidateHosts()
        }
    }

    /**
     * Maps an icon resource ID to a cellular network type (e.g. 5G/4G/3G) and associates it with its SIM slot.
     */
    private fun noteSlotType(view: View, resId: Int) {
        val type = StatusState.typeOfIcon(view.resources, resId) ?: return
        val group = mobileGroupOf(view) ?: return
        if (group.childCount !in 1..StatusState.SIM_SLOTS) return
        var child: View = view
        while (child.parent !== group) {
            child = child.parent as? View ?: return
        }
        StatusState.setSlotType(group.indexOfChild(child), type)
    }

    /**
     * Walks up the view hierarchy from the signal view to locate the parent `mobileGroupId` container.
     */
    private fun mobileGroupOf(view: View): ViewGroup? {
        if (!StatusBarIconManager.idsResolved || StatusBarIconManager.mobileGroupId == 0) return null
        var p = view.parent
        while (p != null) {
            if (p is ViewGroup && p.id == StatusBarIconManager.mobileGroupId) {
                return p
            }
            p = (p as? View)?.parent
        }
        return null
    }

    /**
     * Hooks `SignalUnitNormalView.updateView` to hide native network type labels when module owns them.
     */
    private fun hookTypeViews(module: XposedModule, cl: ClassLoader): Int {
        val unit = Refl.cls("com.android.systemui.statusbar.SignalUnitNormalView", cl) ?: return 0
        sUnitTypeField = Refl.field(unit, "mMobileType")
        return StatusHooks.hook(module, Refl.methodNamed(unit, "updateView", 2), "honorduo-unit-type") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            val target = chain.getArg(0)
            val a = ModuleConfig.appearance()
            if ((a.glyph || a.typeOutOfRing) && a.ownsType() && self != null && target != null) {
                val type = Refl.get(sUnitTypeField, self)
                if (type === target && type is View) {
                    StatusBarIconManager.hide(type)
                }
            }
            result
        }
    }
}
