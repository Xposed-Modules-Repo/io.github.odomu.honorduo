package io.github.odomu.honorduo.hook

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.TrioConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import androidx.core.graphics.drawable.toDrawable

/**
 * Replaces native status bar icons with custom built-in assets for Wi-Fi and system status indicators.
 *
 * Hooks:
 * - [Resources.getDrawableForDensity]: Intercepts density-specific drawable lookups.
 * - [Resources.getDrawable]: Intercepts standard drawable lookups.
 * - [TypedArray.getDrawable]: Intercepts style attribute drawable resolution for Wi-Fi icons.
 *
 * Assets:
 * - Packaged inside the module's own APK under `assets/wifi/` (59 icons) and `assets/icons/` (13 icons).
 * - Decoded with `inDensity = 480` to let Android automatically scale drawables to device screen density.
 * - Decoded bitmaps are cached by resource entry name in thread-safe [ConcurrentHashMap] instances.
 */
internal object StatusBarIconHook {

    private const val ASSET_WIFI_PREFIX = "assets/wifi/"
    private const val ASSET_STATUS_PREFIX = "assets/icons/"
    private const val BITMAP_IN_DENSITY = 480

    /**
     * The resource entries the alarm icon is drawn from.
     *
     * Matched by name rather than by view id: the status bar builds these icons from
     * drawables, and there is no `alarm` id in the icon row to compare against.
     */
    private val ALARM_ENTRIES = setOf("stat_sys_alarm", "stat_sys_alarm_dim")

    /** The resource entries the Bluetooth icon is drawn from. */
    private val BLUETOOTH_ENTRIES = setOf(
        "stat_sys_data_bluetooth",
        "stat_sys_data_bluetooth_connected",
    )

    /** Dynamic switch state for Wi-Fi icon replacement. */
    @Volatile
    private var sWifiEnabled = false

    /** Dynamic switch state for system status icon replacement. */
    @Volatile
    private var sStatusEnabled = false

    /** Dynamic switch state for hiding the alarm icon. */
    @Volatile
    private var sHideAlarm = false

    /** Dynamic switch state for hiding the Bluetooth icon. */
    @Volatile
    private var sHideBluetooth = false

    /** Path to module's own APK file, captured at install time to open assets zip. */
    @Volatile
    private var sModuleApkPath: String? = null

    /** Lazy-opened ZipFile handle for reading module assets directly from APK. */
    @Volatile
    private var sModuleZip: ZipFile? = null

    private val sWifiCache = ConcurrentHashMap<String, Bitmap>()
    private val sStatusCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Installs resource drawable hooks into the target ClassLoader.
     *
     * @param module Active Xposed module instance used to register hooks.
     * @param cl SystemUI ClassLoader containing Resources and TypedArray.
     * @return Total count of successfully installed hooks.
     */
    fun install(module: XposedModule, cl: ClassLoader): Int {
        val a = TrioConfig.appearance()
        sWifiEnabled = a.replaceWifiIcon
        sStatusEnabled = a.replaceStatusIcon
        sHideAlarm = a.hideAlarmIcon
        sHideBluetooth = a.hideBluetoothIcon
        sModuleApkPath = module.moduleApplicationInfo.sourceDir

        var hooked = 0
        val resClass = Refl.cls("android.content.res.Resources", cl) ?: Resources::class.java
        val taClass = Refl.cls("android.content.res.TypedArray", cl) ?: TypedArray::class.java

        // Hook Resources.getDrawableForDensity(int, int, Theme)
        val mDensity = Refl.method(
            resClass,
            "getDrawableForDensity",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Resources.Theme::class.java,
        )
        hooked += TrioHooks.hook(module, mDensity, "honorduo-icon-drawable-density") { chain ->
            val result = chain.proceed()
            val res = chain.thisObject as? Resources ?: return@hook result
            val resId = chain.getArg(0) as? Int ?: return@hook result
            val replacement = lookup(res, resId, wifiOnly = false)
            replacement ?: result
        }

        // Hook Resources.getDrawable(int, Theme)
        val mDrawable = Refl.method(
            resClass,
            "getDrawable",
            Int::class.javaPrimitiveType!!,
            Resources.Theme::class.java,
        )
        hooked += TrioHooks.hook(module, mDrawable, "honorduo-icon-drawable") { chain ->
            val result = chain.proceed()
            val res = chain.thisObject as? Resources ?: return@hook result
            val resId = chain.getArg(0) as? Int ?: return@hook result
            val replacement = lookup(res, resId, wifiOnly = false)
            replacement ?: result
        }

        // Hook TypedArray.getDrawable(int) for styled Wi-Fi indicators
        val mTypedArray = Refl.method(
            taClass,
            "getDrawable",
            Int::class.javaPrimitiveType!!,
        )
        hooked += TrioHooks.hook(module, mTypedArray, "honorduo-icon-ta-drawable") { chain ->
            val result = chain.proceed()
            val ta = chain.thisObject as? TypedArray ?: return@hook result
            val index = chain.getArg(0) as? Int ?: return@hook result
            val resId = runCatching { ta.getResourceId(index, 0) }.getOrNull() ?: 0
            if (resId == 0 || !sWifiEnabled) return@hook result
            val replacement = lookup(ta.resources, resId, wifiOnly = true)
            replacement ?: result
        }

        return hooked
    }

    /**
     * Updates replacement enablement dynamically on configuration change without requiring reboot.
     */
    fun updateConfig(
        replaceWifi: Boolean,
        replaceStatus: Boolean,
        hideAlarm: Boolean,
        hideBluetooth: Boolean,
    ) {
        sWifiEnabled = replaceWifi
        sStatusEnabled = replaceStatus
        sHideAlarm = hideAlarm
        sHideBluetooth = hideBluetooth
    }

    /**
     * Look up replacement drawable by matching resource entry name against asset catalogs.
     */
    private fun lookup(resources: Resources, resId: Int, wifiOnly: Boolean): Drawable? {
        // The name below costs a reflective call, and this hook sees every drawable the
        // host resolves, so the switches are checked before it.
        if (!sWifiEnabled && !sStatusEnabled && !sHideAlarm && !sHideBluetooth) return null
        val entry = runCatching { resources.getResourceEntryName(resId) }.getOrNull() ?: return null

        // A hidden icon is handed back as a transparent drawable: the status bar draws
        // whatever this returns, and one with no intrinsic size collapses the slot
        // instead of leaving a gap where the icon was.
        if (!wifiOnly) {
            val hide = (sHideAlarm && entry in ALARM_ENTRIES) ||
                (sHideBluetooth && entry in BLUETOOTH_ENTRIES)
            if (hide) return ColorDrawable(Color.TRANSPARENT)
        }

        if (!sWifiEnabled && !sStatusEnabled) return null

        if (sWifiEnabled) {
            getIcon(ASSET_WIFI_PREFIX, entry, sWifiCache)?.let {
                return it.toDrawable(resources)
            }
        }

        if (!wifiOnly && sStatusEnabled) {
            getIcon(ASSET_STATUS_PREFIX, entry, sStatusCache)?.let {
                return it.toDrawable(resources)
            }
        }

        return null
    }

    /**
     * Decodes icon bitmap from module APK asset zip or returns from cache.
     */
    private fun getIcon(prefix: String, entry: String, cache: ConcurrentHashMap<String, Bitmap>): Bitmap? {
        cache[entry]?.let { return it }
        val zip = ensureZip() ?: return null
        return runCatching {
            val ze: ZipEntry? = zip.getEntry("$prefix$entry.png")
            ze?.let {
                zip.getInputStream(it).use { input ->
                    val opts = BitmapFactory.Options().apply { inDensity = BITMAP_IN_DENSITY }
                    BitmapFactory.decodeStream(input, null, opts)?.also { bmp ->
                        cache[entry] = bmp
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * Lazily opens the module APK zip file safely using double-checked locking.
     */
    private fun ensureZip(): ZipFile? {
        sModuleZip?.let { return it }
        synchronized(this) {
            sModuleZip?.let { return it }
            val path = sModuleApkPath ?: return null
            return runCatching {
                if (File(path).exists()) ZipFile(path) else null
            }.getOrNull()?.also { sModuleZip = it }
        }
    }
}
