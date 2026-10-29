package io.github.odomu.honorduo.ui.screen

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import io.github.libxposed.service.XposedService
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.component.ActivatedBadge
import io.github.odomu.honorduo.ui.component.ActivatedBadgeDark
import io.github.odomu.honorduo.ui.component.ActivatedContainer
import io.github.odomu.honorduo.ui.component.ActivatedContainerDark
import io.github.odomu.honorduo.ui.component.ActivatedContent
import io.github.odomu.honorduo.ui.component.GlassDropdownPreference
import io.github.odomu.honorduo.ui.component.SettingTile
import io.github.odomu.honorduo.ui.component.SettingsItemMargin
import io.github.odomu.honorduo.ui.component.isDarkTheme
import io.github.odomu.honorduo.ui.control.SettingsPart
import io.github.odomu.honorduo.ui.dialog.ReleaseNotesLoader
import io.github.odomu.honorduo.ui.manager.HAS_REPO
import io.github.odomu.honorduo.ui.manager.REPO_HOME
import io.github.odomu.honorduo.ui.manager.SettingsRepository
import io.github.odomu.honorduo.ui.manager.UpdateController
import io.github.odomu.honorduo.ui.manager.UpdateState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.io.File

/**
 * The settings grid: one tile per sheet, two to a row.
 *
 * The order is the reading order of the grid, so a pair is always (left, right).
 */
private class SettingEntry(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val part: SettingsPart,
)

private val SETTING_ENTRIES = listOf(
    SettingEntry(
        R.string.section_appearance, R.string.card_appearance_summary,
        R.drawable.ic_card_appearance, SettingsPart.Appearance,
    ),
    SettingEntry(
        R.string.drawing_part_shade, R.string.card_shade_summary,
        R.drawable.ic_lucide_sliders_horizontal, SettingsPart.Shade,
    ),
    SettingEntry(
        R.string.card_battery_title, R.string.card_battery_summary,
        R.drawable.ic_card_battery, SettingsPart.Battery,
    ),
    SettingEntry(
        R.string.card_wifi_title, R.string.card_wifi_summary,
        R.drawable.ic_card_wifi, SettingsPart.Wifi,
    ),
    SettingEntry(
        R.string.card_mobile_title, R.string.card_mobile_summary,
        R.drawable.ic_card_signal, SettingsPart.Mobile,
    ),
    SettingEntry(
        R.string.card_type_title, R.string.card_type_summary,
        R.drawable.ic_card_type, SettingsPart.Type,
    ),
    SettingEntry(
        R.string.card_readouts_title, R.string.card_readouts_summary,
        R.drawable.ic_card_readouts, SettingsPart.Readouts,
    ),
    SettingEntry(
        R.string.card_clock_title, R.string.card_clock_summary,
        R.drawable.ic_card_clock, SettingsPart.Clock,
    ),
)

/** Settings tab: a two-column grid of tiles, each opening its own sheet. */
internal fun LazyListScope.settingsTab(onOpenPart: (SettingsPart) -> Unit) {
    SETTING_ENTRIES.chunked(2).forEach { pair ->
        item(key = "tile-${pair[0].title}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { entry ->
                    SettingTile(
                        title = stringResource(entry.title),
                        summary = stringResource(entry.summary),
                        icon = ImageVector.vectorResource(entry.icon),
                        onClick = { onOpenPart(entry.part) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd count would leave the last tile stretched across the row; a spacer
                // keeps it half-width and in its column.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Home page: activation banner, activation state, info, backup & restore, logs, about, font and nav styles. */
internal fun LazyListScope.aboutTab(
    settings: ModuleSettings,
    repository: SettingsRepository,
    service: XposedService?,
    updater: UpdateController,
    installingFont: Boolean,
    onChooseFont: () -> Unit,
    onClearFont: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAboutPage: () -> Unit,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val isActivated = service != null
    val scopes = runCatching { service?.scope }.getOrNull() ?: emptyList()
    val hasSystemUiScope = scopes.contains("com.android.systemui")
    if (!isActivated) {
        item {
            ActivationBanner(
                title = stringResource(R.string.home_banner_inactive_title),
                summary = stringResource(R.string.home_banner_inactive_summary),
                isWarning = true,
            )
        }
    } else if (!hasSystemUiScope) {
        item {
            ActivationBanner(
                title = stringResource(R.string.home_banner_noscope_title),
                summary = stringResource(R.string.home_banner_noscope_summary),
                isWarning = true,
            )
        }
    }

    item { ActivatedRow(settings = settings, service = service) }
    item { InfoCard(service = service) }

    item { Spacer(Modifier.height(12.dp)) }
    item {
        Card {
            LauncherIconEntry()
            ArrowPreference(
                title = stringResource(R.string.backup_export_title),
                summary = stringResource(R.string.backup_export_summary),
                insideMargin = SettingsItemMargin,
                onClick = onExportBackup,
            )
            ArrowPreference(
                title = stringResource(R.string.backup_import_title),
                summary = stringResource(R.string.backup_import_summary),
                insideMargin = SettingsItemMargin,
                onClick = onImportBackup,
            )
            ArrowPreference(
                title = stringResource(R.string.log_viewer_title),
                summary = stringResource(R.string.log_viewer_summary),
                insideMargin = SettingsItemMargin,
                onClick = onOpenLogs,
            )
            val aboutSummary = when (val s = updater.state) {
                is UpdateState.Checking -> stringResource(R.string.update_checking)
                is UpdateState.UpToDate -> stringResource(R.string.update_uptodate, s.current)
                is UpdateState.Available -> stringResource(R.string.update_available, s.info.version)
                is UpdateState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%"
                is UpdateState.Ready -> "新版本已下载完成，点击安装"
                is UpdateState.NoRelease -> stringResource(R.string.update_no_release)
                else -> stringResource(R.string.view_repository_summary)
            }
            ArrowPreference(
                title = stringResource(R.string.about_title),
                summary = aboutSummary,
                insideMargin = SettingsItemMargin,
                endActions = {
                    if (updater.state is UpdateState.Checking || updater.state is UpdateState.Downloading) {
                        CircularProgressIndicator(size = 18.dp, modifier = Modifier.padding(end = 4.dp))
                    }
                },
                onClick = onOpenAboutPage,
            )
        }
    }

    item { Spacer(Modifier.height(12.dp)) }
    item {
        Card {
            GlassDropdownPreference(
                title = stringResource(R.string.language_title),
                items = listOf(
                    stringResource(R.string.language_system),
                    stringResource(R.string.language_zh),
                    stringResource(R.string.language_en),
                ),
                selectedIndex = repository.getAppLanguage(),
                insideMargin = SettingsItemMargin,
            ) { index ->
                repository.setAppLanguage(index)
                (repository.context as? Activity)?.recreate()
            }
            GlassDropdownPreference(
                title = stringResource(R.string.nav_style_title),
                items = listOf(
                    stringResource(R.string.nav_style_docked),
                    stringResource(R.string.nav_style_floating),
                    stringResource(R.string.nav_style_liquid),
                ),
                selectedIndex = Prefs.normalizeNavStyle(settings.navStyle),
                insideMargin = SettingsItemMargin,
            ) { index -> update { it.setNavStyle(index) } }
            GlassDropdownPreference(
                title = stringResource(R.string.nav_labels_title),
                items = listOf(
                    stringResource(R.string.nav_labels_both),
                    stringResource(R.string.nav_labels_icon),
                    stringResource(R.string.nav_labels_text),
                ),
                selectedIndex = Prefs.normalizeNavLabels(settings.navLabels),
                insideMargin = SettingsItemMargin,
            ) { index -> update { it.setNavLabels(index) } }
        }
    }

    item { Spacer(Modifier.height(12.dp)) }
    item {
        Card {
            val chosen = settings.fontPath
            ArrowPreference(
                title = stringResource(R.string.font_title),
                summary = when {
                    installingFont -> stringResource(R.string.font_installing)
                    chosen.isEmpty() -> stringResource(R.string.font_summary_system)
                    else -> File(chosen).name
                },
                insideMargin = SettingsItemMargin,
                enabled = !installingFont,
                onClick = onChooseFont,
            )
            val nothingToClear = chosen.isEmpty()
            ArrowPreference(
                title = stringResource(R.string.font_clear_title),
                summary = if (nothingToClear) {
                    stringResource(R.string.font_clear_summary_idle)
                } else {
                    stringResource(R.string.font_clear_summary)
                },
                insideMargin = SettingsItemMargin,
                enabled = !nothingToClear && !installingFont,
                onClick = { onClearFont(chosen) },
            )
        }
    }
}

@Composable
fun ActivationBanner(
    title: String,
    summary: String,
    isWarning: Boolean = true,
) {
    val bg = if (isWarning) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val titleColor = if (isWarning) Color(0xFFC62828) else Color(0xFF2E7D32)
    val contentColor = if (isWarning) Color(0xFFB71C1C) else Color(0xFF1B5E20)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = if (isDarkTheme()) Color(0xFF2C1E1E) else bg,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2,
                color = if (isDarkTheme()) Color(0xFFEF9A9A) else titleColor,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = if (isDarkTheme()) Color(0xFFE57373) else contentColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun ActivatedRow(settings: ModuleSettings, service: XposedService?) {
    val context = LocalContext.current
    val module = remember(context) { readModuleInfo(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivatedCard(
            active = service != null,
            version = module.moduleVersion,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = stringResource(R.string.about_hook_count),
                value = hookCount(service).toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.about_enabled_count),
                value = enabledCount(settings).toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ActivatedCard(active: Boolean, version: String, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val content = if (dark) MiuixTheme.colorScheme.onSurface else ActivatedContent

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.defaultColors(
            color = if (dark) ActivatedContainerDark else ActivatedContainer,
            contentColor = content,
        ),
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (active) R.string.about_activated else R.string.about_inactive,
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (active) {
                        stringResource(R.string.about_module_version, version)
                    } else {
                        stringResource(R.string.about_inactive_hint)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check_circle_outline),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (dark) ActivatedBadgeDark else ActivatedBadge),
                    modifier = Modifier.size(110.dp),
                )
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, bottomPadding: Dp = 24.dp) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 2.dp),
    )
    Text(
        text = value,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(bottom = bottomPadding),
    )
}

@Composable
fun LauncherIconEntry() {
    val context = LocalContext.current
    var hidden by remember(context) { mutableStateOf(isLauncherIconHidden(context)) }
    var showConfirm by remember { mutableStateOf(false) }

    SwitchPreference(
        checked = hidden,
        onCheckedChange = { value ->
            if (value) {
                showConfirm = true
            } else {
                setLauncherIconHidden(context, false)
                hidden = isLauncherIconHidden(context)
            }
        },
        title = stringResource(R.string.about_hide_icon),
        summary = stringResource(R.string.about_hide_icon_summary),
        insideMargin = SettingsItemMargin,
    )

    if (showConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.hide_icon_confirm_title),
            summary = stringResource(R.string.hide_icon_confirm_summary),
            onDismissRequest = { showConfirm = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showConfirm = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        showConfirm = false
                        setLauncherIconHidden(context, true)
                        hidden = isLauncherIconHidden(context)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.hide_icon_confirm_action))
                }
            }
        }
    }
}

private fun launcherAlias(context: Context): ComponentName =
    ComponentName(context.packageName, Prefs.LAUNCHER_ALIAS)

private fun isLauncherIconHidden(context: Context): Boolean = try {
    context.packageManager.getComponentEnabledSetting(launcherAlias(context)) ==
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
} catch (_: Throwable) {
    false
}

private fun setLauncherIconHidden(context: Context, hidden: Boolean) {
    try {
        context.packageManager.setComponentEnabledSetting(
            launcherAlias(context),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    } catch (_: Throwable) {
    }
}

@Composable
fun InfoCard(service: XposedService?) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.info_unknown)
    val module = remember(context) { readModuleInfo(context) }
    val framework = remember(service, unknown) { readFrameworkInfo(service, unknown) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoRow(stringResource(R.string.info_module_version), module.moduleVersion)
            InfoRow(stringResource(R.string.info_xposed_version), framework)
            InfoRow(stringResource(R.string.info_device_model), module.deviceModel)
            InfoRow(stringResource(R.string.info_android_version), module.androidVersion)
            InfoRow(
                label = stringResource(R.string.info_system_fingerprint),
                value = module.systemFingerprint,
                bottomPadding = 4.dp,
            )
        }
    }
}

data class ModuleInfo(
    val moduleVersion: String,
    val systemFingerprint: String,
    val androidVersion: String,
    val deviceModel: String,
)

fun readModuleInfo(context: Context): ModuleInfo {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = packageInfo?.versionName ?: "?"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val systemVersion = listOf(Build.VERSION.INCREMENTAL, Build.DISPLAY)
        .filter { !it.isNullOrBlank() }
        .distinct()
        .joinToString(" · ")
    return ModuleInfo(
        moduleVersion = "$versionName ($versionCode)",
        systemFingerprint = Build.FINGERPRINT.ifBlank { "?" },
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel = readDeviceName(context),
    )
}

fun readDeviceName(context: Context): String {
    val name = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty()
}

fun hookCount(service: XposedService?): Int {
    if (service == null) return 0
    return 32
}

fun enabledCount(settings: ModuleSettings): Int {
    val a = StatusAppearance.of(settings)
    if (!a.enabled) return 0
    return listOf(
        a.wifi || a.replaceWifiIcon,
        a.mobile,
        a.dualSim,
        a.value,
        a.drawsBolt(),
        a.typeAnywhere(),
        a.perfPower || a.perfRunning,
        a.perfTemp,
        a.perfCurrent,
        a.perfFps,
        a.clockSeconds || a.clockDate || a.clockWeekday || a.clockLunar || a.clockWeather,
        a.shadeHidePower || a.shadeHideBell || a.shadeLunar || a.shadeWeather,
        a.replaceStatusIcon || a.hideAlarmIcon || a.hideBluetoothIcon,
    ).count { it }
}

fun readFrameworkInfo(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    return runCatching {
        val name = service.frameworkName
        val version = service.frameworkVersion
        val code = service.frameworkVersionCode
        "$name $version ($code) API ${service.apiVersion}"
    }.getOrDefault(unknown)
}

/**
 * The in-app updater card.
 */
@Composable
internal fun UpdateCard(
    updater: UpdateController,
    showMessage: (String) -> Unit,
    onCheck: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    val state = updater.state
    val isDownloading = state is UpdateState.Downloading
    val isChecking = state is UpdateState.Checking
    val isAvailable = state is UpdateState.Available
    val context = LocalContext.current
    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.0.4"
    }
    val scope = rememberCoroutineScope()
    var isFetchingNotes by remember { mutableStateOf(false) }

    val status: String? = when (state) {
        is UpdateState.Idle -> stringResource(R.string.update_summary_idle)
        is UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.UpToDate -> stringResource(R.string.update_uptodate, state.current)
        is UpdateState.NoRelease -> stringResource(R.string.update_no_release)
        is UpdateState.Available -> null
        is UpdateState.Downloading -> stringResource(
            R.string.update_downloading,
            (state.progress * 100f).toInt(),
        )
        is UpdateState.Ready -> stringResource(R.string.update_download_completed)
        is UpdateState.Failed -> listOfNotNull(
            stringResource(
                if (HAS_REPO) R.string.update_failed else R.string.update_unconfigured,
            ),
            state.detail?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    val updateNotice: (@Composable () -> Unit)? = when (state) {
        is UpdateState.Downloading -> {
            {
                top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
                    progress = state.progress.takeIf { it > 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        is UpdateState.Available -> {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.update_available, state.info.version),
                        fontSize = 13.sp,
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        else -> null
    }

    Card {
        ArrowPreference(
            title = stringResource(R.string.update_title),
            summary = status,
            insideMargin = SettingsItemMargin,
            enabled = !isDownloading,
            endActions = {
                if (isChecking) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 20.dp)
                    }
                }
            },
            bottomAction = updateNotice,
            onClick = onCheck,
        )

        ArrowPreference(
            title = stringResource(R.string.release_notes_title),
            summary = stringResource(R.string.release_notes_summary),
            insideMargin = SettingsItemMargin,
            endActions = {
                if (isFetchingNotes) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 20.dp)
                    }
                }
            },
            onClick = {
                scope.launch {
                    isFetchingNotes = true
                    ReleaseNotesLoader.fetch(targetVersion = currentVersion, forceRefresh = true)
                    isFetchingNotes = false
                    onOpenReleaseNotes()
                }
            },
        )

        if (HAS_REPO) {
            ArrowPreference(
                title = stringResource(R.string.update_repo),
                summary = stringResource(R.string.update_repo_summary),
                insideMargin = SettingsItemMargin,
                onClick = { updater.openInBrowser(REPO_HOME) },
            )
        }
    }
}
