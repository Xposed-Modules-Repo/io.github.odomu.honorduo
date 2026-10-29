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

package io.github.odomu.honorduo.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import io.github.libxposed.service.XposedService
import io.github.odomu.honorduo.HonorDuoApp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.component.LocalDropdownBackdrop
import io.github.odomu.honorduo.ui.component.SectionList
import io.github.odomu.honorduo.ui.control.SettingsPart
import io.github.odomu.honorduo.ui.dialog.ColorDialog
import io.github.odomu.honorduo.ui.dialog.PartSettingsSheet
import io.github.odomu.honorduo.ui.dialog.ReleaseNotesDialog
import io.github.odomu.honorduo.ui.dialog.ResetColorsDialog
import io.github.odomu.honorduo.ui.dialog.RestartDialog
import io.github.odomu.honorduo.ui.dialog.RoleColor
import io.github.odomu.honorduo.ui.drawing.SceneSelector
import io.github.odomu.honorduo.ui.manager.FontInstaller
import io.github.odomu.honorduo.ui.manager.HAS_REPO
import io.github.odomu.honorduo.ui.manager.InstallResult
import io.github.odomu.honorduo.ui.manager.RestartController
import io.github.odomu.honorduo.ui.manager.RestartState
import io.github.odomu.honorduo.ui.manager.SettingsRepository
import io.github.odomu.honorduo.ui.manager.UpdateController
import io.github.odomu.honorduo.ui.manager.UpdateState
import io.github.odomu.honorduo.ui.manager.rememberBackupManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Inner padding of a preference row inside its Card. Miuix's Card carries no
 * outer margin and its preferences default to a uniform 16.dp box; the MagicOS look
 * wants 18.dp against the card edge and a slightly tighter 14.dp vertical rhythm.
 *
 * `internal` rather than private because the drawing page lays out rows
 * from `PartControls.kt`, and the two pages have to agree about the
 * rhythm or a shared row would sit differently depending on who drew it.
 */

/**
 * Which role colour the picker dialog is editing.
 *
 * `internal` rather than private because the drawing page's long-press
 * dialog offers the same colours, and it opens the same picker: a second enum
 * would be a second thing to keep in step with the switch table.
 */

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    // Kept as explicit state objects, not just `by` delegates, because the nav
    // entries below are built once and remembered: an entry that closed over the
    // *value* of settings would keep rendering the snapshot it was built with.
    // Handing the entries these objects makes every read a real snapshot read,
    // taken while the destination composes.
    val settingsState = remember { mutableStateOf(repository.read()) }
    var settings by settingsState
    val serviceState = remember { mutableStateOf(HonorDuoApp.xposedService) }
    var service by serviceState
    var editing by remember { mutableStateOf<RoleColor?>(null) }
    var resettingColors by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    var activeSettingPart by remember { mutableStateOf<SettingsPart?>(null) }

    // The section the page is on *is* the top of the back stack, rather than a
    // second piece of state kept in step with it: the strip and the system back
    // gesture then read and write the same list, so they cannot disagree about
    // where the user is.
    //
    // The supertype is spelled out because the reified parameter is otherwise
    // inferred from the argument, and a stack declared as the first concrete
    // route cannot hold the other three once the process is recreated.
    val isFirst = remember { repository.isFirstLaunch() }
    LaunchedEffect(Unit) {
        if (isFirst) {
            repository.markFirstLaunchDone()
        }
    }
    val initialRoute = if (isFirst) Route.About else Route.Layout
    val nav = rememberNavController<Route>(initialRoute)
    val openTab = SettingsTab.entries.firstOrNull {
        it.route == nav.backStack.lastOrNull()
    } ?: if (isFirst) SettingsTab.About else SettingsTab.Layout
    val pageStateHolder = rememberSaveableStateHolder()
    BackHandler(enabled = nav.backStack.size > 1) { nav.pop() }

    // The framework service can bind or die at any moment; both the status row
    // and every remote write have to follow it.
    DisposableEffect(repository) {
        val listener: (XposedService?) -> Unit = { service = it }
        HonorDuoApp.addServiceListener(listener)
        onDispose { HonorDuoApp.removeServiceListener(listener) }
    }

    // Held here rather than inside the About tab: a LazyColumn disposes items
    // that scroll out of view, and a download in flight must survive the user
    // flicking over to another tab and back.
    val context = LocalContext.current
    val updater = remember(context) { UpdateController(context.applicationContext) }
    DisposableEffect(updater) { onDispose { updater.dispose() } }

    // Same reason as the updater itself: the "release notes" dialog is dismissed by the user,
    // and scrolling the card out of view disposes the item that would otherwise hold that
    // answer. Kept for the process lifetime, keyed by version - a newer release has a
    // different version string, so its notes open again, which is the point: it is different
    // news.
    var showingReleaseNotes by remember { mutableStateOf(false) }
    var showingLogs by remember { mutableStateOf(false) }
    var showingAboutPage by remember { mutableStateOf(false) }
    val ignoredVersion = remember { repository.getIgnoreUpdateVersion() }
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(ignoredVersion.takeIf { it.isNotBlank() }) }
    var dontRemindUpdate by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    }

    val msgBackupExportSuccess = stringResource(R.string.backup_export_success)
    val msgBackupFailed = stringResource(R.string.backup_failed)
    val msgBackupImportSuccess = stringResource(R.string.backup_import_success)
    val msgUpdateDownloadCompleted = stringResource(R.string.update_download_completed)
    val msgUpdatePermissionNeeded = stringResource(R.string.update_permission_needed)
    val msgUpdateInstallFailed = stringResource(R.string.update_install_failed)
    val msgFontApplied = stringResource(R.string.font_applied)
    val msgFontNotAFont = stringResource(R.string.font_not_a_font)
    val msgFontFailed = stringResource(R.string.font_failed)
    val restartDone = stringResource(R.string.restart_done)

    val backupManager = rememberBackupManager(
        context = context,
        repository = repository,
        onExportSuccess = { showMessage(msgBackupExportSuccess) },
        onImportSuccess = { count ->
            settings = repository.read()
            showMessage(String.format(msgBackupImportSuccess, count))
        },
        onError = { message -> showMessage(String.format(msgBackupFailed, message)) },
    )
    LaunchedEffect(Unit) {
        if (HAS_REPO) {
            updater.check()
        }
    }

    // Same reasoning as the updater: the dialog can be dismissed mid-kill, and a
    // root prompt that outlives the dialog still has to land somewhere.
    val restarter = remember { RestartController() }
    DisposableEffect(restarter) { onDispose { restarter.dispose() } }

    // Single funnel: apply the write, then re-read so the preview never shows a
    // value the repository did not accept (clamping happens on read).
    fun update(block: (SettingsRepository) -> Unit) {
        block(repository)
        settings = repository.read()
    }

    // Switching section: tapping the one already open is a no-op rather than a
    
    // the strip accepts taps faster than a transition runs. Going back to a
    // section further down walks back to it instead of pushing it again, so the
    // stack stays a path through the page rather than a log of every tap.
    fun selectTab(next: SettingsTab) {
        val backStack = nav.backStack
        if (backStack.lastOrNull() == next.route) return
        if (backStack.contains(next.route)) {
            nav.popUntil { key -> key == next.route }
        } else {
            nav.push(next.route)
        }
    }


    // Miuix's own host rather than a system Toast: the snackbar is drawn on the
    // same surface as the page that raised it, so it follows the theme and dies
    // with the screen instead of hovering over whatever comes next.
    //
    // Short (4s) rather than Long (10s): these messages are one short
    // sentence, and ten seconds is a long time to look at one. The user can
    // swipe the bar away if even four is too long.

    var lastInstalledPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(updater.state) {
        val ready = updater.state as? UpdateState.Ready ?: return@LaunchedEffect
        val path = ready.file.absolutePath
        if (path == lastInstalledPath) return@LaunchedEffect
        lastInstalledPath = path
        showMessage(msgUpdateDownloadCompleted)
        when (updater.install(ready.file)) {
            InstallResult.Started -> Unit
            InstallResult.NeedsPermission -> {
                showMessage(msgUpdatePermissionNeeded)
                updater.openInstallPermissionScreen()
            }
            InstallResult.NoInstaller -> {
                showMessage(msgUpdateInstallFailed)
            }
        }
    }

    var installingFont by rememberSaveable { mutableStateOf(false) }
    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        installingFont = true
        scope.launch {
            when (val result = FontInstaller.install(context, uri)) {
                is FontInstaller.Result.Installed -> {
                    val previous = settingsState.value.fontPath
                    if (previous.isNotEmpty() && previous != result.path) {
                        FontInstaller.remove(previous)
                    }
                    update { it.setFontPath(result.path) }
                    showMessage(String.format(msgFontApplied, result.name))
                }
                FontInstaller.Result.NotAFont ->
                    showMessage(msgFontNotAFont)
                is FontInstaller.Result.Failed -> {
                    showMessage(msgFontFailed)
                }
            }
            installingFont = false
        }
    }

    // The kill lands on System UI, not on this process, so the confirmation
    // outlives the dialog and has to be shown from here.
    LaunchedEffect(restarter.state) {
        if (restarter.state is RestartState.Done) {
            showMessage(restartDone)
            restarting = false
        }
    }

    // Hoisted out of the draw lambda below, which is not a composable scope and
    // so cannot read the theme itself.
    val surfaceColor = MiuixTheme.colorScheme.surface

    // Blur needs a runtime shader (API 33). The library skips the effect itself
    // on older devices, but that is not enough here: the blur is what replaces
    // the bar's background, and the bar applies the modifier before that
    // background, so an un-gated bar would end up with no background at all and
    val progressiveBlurShader = remember(context) { loadProgressiveBlurShader(context) }
    val barBlurSupported = isRuntimeShaderSupported()

    // Capture the animated pages for the stationary bottom bar. Each page has a
    // separate content backdrop for its own top bar, keeping sampling acyclic.
    val navBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    CompositionLocalProvider(LocalDropdownBackdrop provides navBackdrop) {
        if (showingLogs) {
            BackHandler { showingLogs = false }
            LogViewerPage(
                repository = repository,
                onBack = { showingLogs = false },
                showMessage = showMessage,
            )
        } else if (showingAboutPage) {
            BackHandler { showingAboutPage = false }
            AboutPage(
                updater = updater,
                onBack = { showingAboutPage = false },
                onCheckUpdate = {
                    dismissedUpdateVersion = null
                    lastInstalledPath = null
                    updater.check()
                },
            )
        } else {
            Scaffold(
                bottomBar = {
                val darkTheme = isSystemInDarkTheme()
                val style = Prefs.normalizeNavStyle(settings.navStyle)
                val labels = Prefs.normalizeNavLabels(settings.navLabels)
                val openIndex = openTab.ordinal

                // Three genuinely different widgets, not one widget with three parameter sets.
                // Reading the reference made that clear: its docked bar is Miuix's own
                // `NavigationBar` (which brings the item metrics, the divider policy and the
                // label animation with it), its floating bar is a hand-built capsule, and its
                // liquid style is a three-layer composition that tints a hidden copy of the row.
                // Folding them into one Box, which is what this used to do, cannot produce any
                // of the three.
                when (style) {
                    Prefs.NAV_HYPER_OS -> DockedNavigationBar(
                        labels = labels,
                        openIndex = openIndex,
                        darkTheme = darkTheme,
                        backdrop = if (barBlurSupported) navBackdrop else null,
                        onSelect = { selectTab(SettingsTab.entries[it]) },
                    )

                    Prefs.NAV_LIQUID_GLASS -> LiquidNavigationBar(
                        labels = labels,
                        openIndex = openIndex,
                        darkTheme = darkTheme,
                        backdrop = if (barBlurSupported) navBackdrop else null,
                        onSelect = { selectTab(SettingsTab.entries[it]) },
                    )

                    else -> FloatingNavigationBar(
                        labels = labels,
                        openIndex = openIndex,
                        darkTheme = darkTheme,
                        backdrop = if (barBlurSupported) navBackdrop else null,
                        onSelect = { selectTab(SettingsTab.entries[it]) },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // One navigation per tab, each with its own back stack.
            // enter/exit tracks, including interruption handling on a rapid reversal.
            AnimatedContent(
                targetState = openTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (
                            slideInHorizontally(
                                tween(
                                    280,
                                    easing = FastOutSlowInEasing
                                )
                            ) { direction * it / 7 } +
                                    fadeIn(tween(210)) +
                                    scaleIn(tween(280), initialScale = .985f)
                            ) togetherWith (
                            slideOutHorizontally(
                                tween(
                                    230,
                                    easing = FastOutSlowInEasing
                                )
                            ) { -direction * it / 3 } +
                                    fadeOut(tween(180)) +
                                    scaleOut(tween(230), targetScale = .975f)
                            )
                },
                label = "mainTabNavigation",
                modifier = Modifier
                    .fillMaxSize()
                    .background(surfaceColor)
                    .layerBackdrop(navBackdrop),
            ) { currentTab ->
                val tabBlurRadius by transition.animateFloat(
                    transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
                    label = "mainTabBlur",
                ) { state -> if (state == EnterExitState.Visible) 0f else 12f }
                Box(Modifier
                    .fillMaxSize()
                    .blur(tabBlurRadius.dp)) {
                    pageStateHolder.SaveableStateProvider(currentTab.name) {
                        SettingsPage(
                            tab = currentTab,
                            isTop = currentTab == openTab,
                            bottomPadding = padding.calculateBottomPadding(),
                            onRestart = {
                                restarter.reset()
                                restarting = true
                            },
                        ) {
                            when (currentTab) {
                                SettingsTab.Layout -> drawingTab(
                                    settings = settingsState.value,
                                    update = { block -> update(block) },
                                    onEditColor = { role -> editing = role },
                                    onResetColors = { resettingColors = true },
                                    snapshotForStyle = { style -> repository.readForStyle(style) },
                                )

                                SettingsTab.Settings -> settingsTab(
                                    onOpenPart = { part -> activeSettingPart = part },
                                )

                                SettingsTab.About -> aboutTab(
                                    settings = settingsState.value,
                                    repository = repository,
                                    service = serviceState.value,
                                    updater = updater,
                                    installingFont = installingFont,
                                    onChooseFont = { fontPicker.launch(arrayOf("*/*")) },
                                    onClearFont = { fontPath ->
                                        update { it.setFontPath("") }
                                        scope.launch { FontInstaller.remove(fontPath) }
                                    },
                                    onExportBackup = { backupManager.exportBackup() },
                                    onImportBackup = { backupManager.importBackup() },
                                    onOpenLogs = { showingLogs = true },
                                    onOpenAboutPage = { showingAboutPage = true },
                                ) { block -> update(block) }
                            }
                        }
                    }
                }
            }

            // Must stay inside the Scaffold: MiuixPopupHost reads LocalRootDialogStates,
            // which Scaffold provides. Composed outside this lambda the dialog would
            // register itself on a list nothing ever renders, and silently never appear.
            val current = editing
            if (current != null) {
                ColorDialog(
                    role = current,
                    settings = settings,
                    onPick = { onDark, argb ->
                        update { repo ->
                            when (current) {
                                RoleColor.Critical ->
                                    if (onDark) repo.setColorCriticalOnDark(argb)
                                    else repo.setColorCriticalOnLight(argb)

                                RoleColor.HighTemp ->
                                    if (onDark) repo.setColorHighTempOnDark(argb)
                                    else repo.setColorHighTempOnLight(argb)

                                RoleColor.Low ->
                                    if (onDark) repo.setColorLowOnDark(argb)
                                    else repo.setColorLowOnLight(argb)

                                RoleColor.Charging ->
                                    if (onDark) repo.setColorChargingOnDark(argb)
                                    else repo.setColorChargingOnLight(argb)

                                RoleColor.ClockWeekday ->
                                    if (onDark) repo.setColorClockWeekdayOnDark(argb)
                                    else repo.setColorClockWeekdayOnLight(argb)

                                RoleColor.ClockDate ->
                                    if (onDark) repo.setColorClockDateOnDark(argb)
                                    else repo.setColorClockDateOnLight(argb)

                                RoleColor.ClockLunar ->
                                    if (onDark) repo.setColorClockLunarOnDark(argb)
                                    else repo.setColorClockLunarOnLight(argb)
                                RoleColor.ClockWeather ->
                                    if (onDark) repo.setColorClockWeatherOnDark(argb)
                                    else repo.setColorClockWeatherOnLight(argb)
                            }
                        }
                    },
                    onDismiss = { editing = null },
                )
            }

            RestartDialog(
                controller = restarter,
                show = restarting,
                onDismiss = { restarting = false },
            )

            val activePart = activeSettingPart
            if (activePart != null) {
                var previewScene by rememberSaveable { mutableStateOf(PreviewScene.Normal) }
                val isDark = isSystemInDarkTheme()
                PartSettingsSheet(
                    show = true,
                    part = activePart,
                    settings = settingsState.value,
                    update = { block -> update(block) },
                    onEditColor = { role -> editing = role },
                    onResetColors = { resettingColors = true },
                    onDismiss = { activeSettingPart = null },
                    onDismissFinished = { activeSettingPart = null },
                    preview = {
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            DrawingSettingsPreview(activePart, settingsState.value, previewScene, isDark)
                            SceneSelector(previewScene, { previewScene = it }, Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp))
                        }
                    },
                )
            }

            ResetColorsDialog(
                show = resettingColors,
                onDismiss = { resettingColors = false },
                onConfirm = {
                    update { it.resetRoleColors() }
                    resettingColors = false
                },
            )

            // Hosted here, with the other dialogs, rather than inside UpdateCard: the card is
            // a LazyColumn item, and a dialog owned by an item disappears when the item is
            // recycled. It reads the state the check already produced, so it opens by itself
            // the moment a release turns up.
            val available = updater.state as? UpdateState.Available
            val downloadingInfo = (updater.state as? UpdateState.Downloading)?.info
            val currentInfo = available?.info ?: downloadingInfo
            val downloadProgress = (updater.state as? UpdateState.Downloading)?.progress
            val autoShowUpdate = currentInfo?.takeIf { it.version != dismissedUpdateVersion }
            ReleaseNotesDialog(
                show = showingReleaseNotes || autoShowUpdate != null,
                info = currentInfo,
                currentVersion = available?.current,
                downloadProgress = downloadProgress,
                dontRemind = dontRemindUpdate,
                onDontRemindChange = { checked ->
                    dontRemindUpdate = checked
                    if (checked && currentInfo != null) {
                        repository.setIgnoreUpdateVersion(currentInfo.version)
                    } else {
                        repository.setIgnoreUpdateVersion("")
                    }
                },
                onDismiss = {
                    showingReleaseNotes = false
                    if (autoShowUpdate != null) {
                        dismissedUpdateVersion = autoShowUpdate.version
                    }
                },
                onDownload = { info ->
                    updater.download(info)
                },
            )

        }
        }
    }
}

/**
 * A complete tab page, including its title bar. Only the bottom navigation lives
 * outside this subtree so the title, action and list share the same transition.
 */
@Composable
private fun SettingsPage(
    tab: SettingsTab,
    isTop: Boolean,
    bottomPadding: Dp,
    onRestart: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(isTop) {
        if (isTop) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
    }
    val pageTitle = stringResource(
        when (tab) {
            SettingsTab.About -> R.string.tab_home
            SettingsTab.Layout -> R.string.tab_layout
            SettingsTab.Settings -> R.string.tab_settings
        }
    )
    val restartTitle = stringResource(R.string.restart_title)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val context = LocalContext.current
    val progressiveBlurShader = remember(context) { loadProgressiveBlurShader(context) }
    val barBlurSupported = isRuntimeShaderSupported()
    val pageBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    Scaffold(
        topBar = {
            // Three layers, and the thing that makes the reference look the way it does is
            // that all three are tied to the SCROLL, not to a constant:
            //
            //   collapsedFraction == 0 (page at rest under an expanded bar)
            //       the light 6dp layer is fully present and the 16dp masked layer is
            //       invisible - a bar you can see the page through;
            //   collapsedFraction == 1 (content scrolled under the bar)
            //       the light layer has faded out and the masked layer is fully present -
            //       the bar thickens as things pass beneath it.
            //
            // That hand-off is the effect. Two fixed-opacity layers, which is what this had
            // before, give a bar that is blurry in exactly the same way whether anything is
            // behind it or not - legible, but flat, and that flatness is what "not the same
            // as the reference" looks like.
            val collapsed = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
            // clipToBounds on the container: the masked layer is deliberately drawn taller
            // than the bar so its gradient has room to fade, and without a clip on the
            // parent that overflow is painted below the bar - a hard horizontal edge across
            // the top of the page.
            Box(modifier = Modifier.clipToBounds()) {
                if (barBlurSupported) {
                    // Light pass: present while the bar is expanded, gone once it collapses.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = 1f - collapsed }
                            .drawPlainBackdrop(
                                backdrop = pageBackdrop,
                                shape = { RectangleShape },
                                effects = { blur(6.dp.toPx()) },
                                onDrawSurface = { drawRect(surfaceColor.copy(alpha = 0.55f)) },
                            ),
                    )
                    // Heavy pass: the masked 16dp blur, visible only as the bar collapses.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            // expandDrawHeight BEFORE graphicsLayer: the expansion has to
                            // happen in the measure/layout pass, and graphicsLayer starts a
                            // new layer whose bounds are already fixed. Reversed, the drawn
                            // surface is laid out taller than the bar it lives in and its
                            // overflow shows as a hard edge under the bar - the boundary
                            // that was visible across the top of the page.
                            .expandDrawHeight(1.184625f)
                            .graphicsLayer { alpha = collapsed }
                            .drawPlainBackdrop(
                                backdrop = pageBackdrop,
                                shape = { RectangleShape },
                                effects = {
                                    blur(16.dp.toPx())
                                    runtimeShaderEffect(
                                        key = "progressive-blur-alpha-mask",
                                        shaderString = progressiveBlurShader,
                                        uniformShaderName = "content",
                                    ) { setFloatUniform("size", size.width, size.height) }
                                },
                            ),
                    )
                } else {
                    // A device without runtime shaders gets no backdrop at all, so the
                    // bar has to paint itself: transparent here would let the list scroll
                    // straight through the title.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(surfaceColor),
                    )
                }
                TopAppBar(
                    title = pageTitle,
                    // Transparent on purpose: the bar's own background is now the two
                    // layers above (or the opaque fallback when shaders are unsupported),
                    // and a second opaque pass would hide the blur entirely.
                    color = Color.Transparent,
                    largeTitle = pageTitle,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        GlassIconButton(
                            icon = ImageVector.vectorResource(R.drawable.ic_lucide_refresh_cw),
                            description = restartTitle,
                            backdrop = if (barBlurSupported) pageBackdrop else null,
                            collapsedFraction = collapsed,
                            // The dialog is the only place a kill can be started, so a
                            // stale failure from last time must not greet the user.
                            onClick = onRestart,
                        )
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                // Record the list alone: the top bar samples this backdrop.
                .layerBackdrop(pageBackdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            SectionList(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 12.dp,
                    bottom = bottomPadding + 24.dp,
                ),
                isTop = isTop,
                content = content,
            )
        }
    }
}

/**
 * Vertical alpha mask for the top bar's progressive blur: opaque at the bar's top,
 * fading to nothing by half its (expanded) height.
 *
 * `smoothstep(size.y, size.y * 0.5, coord.y)` runs from 1 at `coord.y == size.y` to 0
 * at half that, which is the direction that matters - the mask has to be DENSE where the
 * bar meets the status bar and empty where the page continues, or the bar would end on a
 * hard edge and read as a painted strip.
 */
private const val SHADER_PROGRESSIVE_BLUR = "shaders/progressive_blur_alpha_mask.agsl"
private var sProgressiveBlurShader: String? = null

private fun loadProgressiveBlurShader(context: Context): String {
    sProgressiveBlurShader?.let { return it }
    val src = runCatching {
        context.assets.open(SHADER_PROGRESSIVE_BLUR).bufferedReader().use { it.readText() }
    }.getOrDefault("")
    sProgressiveBlurShader = src
    return src
}

/**
 * Measures a child TALLER than the space it is given, then reports the original height.
 *
 * For the progressive blur: the mask fades over the expanded height, so there has to BE
 * expanded height for it to fade across. Reporting the original height keeps the layout
 * unchanged - only the drawing surface grew.
 */
private fun Modifier.expandDrawHeight(factor: Float) = layout { measurable, constraints ->
    val expandedHeight = (constraints.maxHeight * factor).toInt()
    val placeable = measurable.measure(
        constraints.copy(minHeight = expandedHeight, maxHeight = expandedHeight),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    description: String,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    collapsedFraction: Float,
    onClick: () -> Unit,
) {
    // The glass appears with the bar, the icon does not. That is the reference's design and
    // it is the right one: at rest the bar is nearly invisible, so a glass disc there would
    // be a grey blob floating on nothing - but the ACTION still has to be findable, so the
    // icon stays fully opaque and simply loses its backing disc until the bar thickens.
    //
    // Two separate bindings, not one: `glassAlpha` drives only the disc, and the icon uses
    // the theme colour at full strength. Tying both to the fraction, which an earlier version
    // did, hid the button completely until the page was scrolled.
    val iconColor = MiuixTheme.colorScheme.onSurface
    val glassTint = MiuixTheme.colorScheme.surface.copy(alpha = 0.80f)
    val glassAlpha by animateFloatAsState(
        targetValue = collapsedFraction.coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "glassIconAlpha",
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(46.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The disc follows the scroll.
        Box(
            modifier = Modifier
                .matchParentSize()
                // clip(CircleShape) on the DRAWN layer, not just on the backdrop's
                // `shape`: that parameter shapes the sampled region, but the surface pass
                // and its antialiased corners are painted over the full square, so the four
                // corners outside the inscribed circle showed as grey spikes.
                .graphicsLayer { alpha = glassAlpha }
                .clip(CircleShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(2.dp.toPx())
                                lens(
                                    refractionHeight = 16.dp.toPx(),
                                    refractionAmount = 32.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    width = 0.75.dp,
                                    blurRadius = 0.4.dp,
                                    alpha = 0.76f,
                                )
                            },
                            // A CIRCLE, not a rect. `shape` only shapes the backdrop pass;
                            // this lambda paints the surface on top of it, and a drawRect
                            // here shows as a grey square behind a round button - the shape
                            // and the paint have to agree.
                            onDrawSurface = { drawCircle(glassTint) },
                        )
                    } else {
                        // No backdrop: a plain circle, again not a rect.
                        Modifier.background(glassTint, CircleShape)
                    },
                ),
        )
        // The icon does not follow the scroll.
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
