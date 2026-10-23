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

package io.github.odomu.honorduo.ui


import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import io.github.odomu.honorduo.HonorDuoApp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioSettings
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.abs
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop

/**
 * Inner padding of a preference row inside its Card. Miuix's Card carries no
 * outer margin and its preferences default to a uniform 16.dp box; the MagicOS look
 * wants 18.dp against the card edge and a slightly tighter 14.dp vertical rhythm.
 *
 * `internal` rather than private because the drawing page lays out rows
 * from `PartControls.kt`, and the two pages have to agree about the
 * rhythm or a shared row would sit differently depending on who drew it.
 */
internal val SettingsItemMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

/**
 * Group heading. Miuix's [SmallTitle] defaults to a 28.dp vertical margin, which
 * reads as a stray indent once the page itself only insets 12.dp; the reference
 * page narrows it to 18.dp so the heading lines up with the card padding instead
 * of the card edge.
 */
@Composable
private fun SectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

/**
 * Which role colour the picker dialog is editing.
 *
 * `internal` rather than private because the drawing page's long-press
 * dialog offers the same colours, and it opens the same picker: a second enum
 * would be a second thing to keep in step with the switch table.
 */
internal enum class RoleColor(val labelRes: Int) {
    Low(R.string.color_low_title),
    Charging(R.string.color_charging_title),
    Critical(R.string.color_critical_title),
    HighTemp(R.string.color_high_temp_title),
    ClockWeekday(R.string.color_clock_weekday_title),
    ClockDate(R.string.color_clock_date_title),
    ClockLunar(R.string.color_clock_lunar_title),
}

/**
 * The page's top-level sections, in strip order. The label reuses the section
 * heading the group already had, so the strip reads as a table of contents for
 * the page rather than a second vocabulary for the same four things.
 *
 * Each section is also a destination on the nav back stack ([route]), which is
 * what gives a section switch its own animated page change and gives the system
 * back gesture something to pop. The routes' names and the strip's names are the
 * same four words, but they are separate types on purpose: the label is a
 * resource id, which has no business living on a value that gets serialized into
 * the saved-state bundle.
 */
private enum class SettingsTab(val labelRes: Int, val route: Route) {
    Layout(R.string.tab_layout, Route.Layout),
    Settings(R.string.tab_settings, Route.Settings),
    About(R.string.about_title, Route.About),
}

/**
 * Destinations of the settings back stack, one per section.
 *
 * Serialized data objects rather than a plain enum or class: miuix-nav keeps its
 * stack in rememberSaveable and rebuilds it through a serializer, so every route
 * has to round-trip. Page content uses its tab name as a separate saveable-state
 * key so the preview's saved choices survive a section switch.
 *
 * over the page, and pushing them would turn the back gesture into "cancel"
 * instead of "go back a section".
 */
@Serializable
private sealed interface Route : NavKey {
    @Serializable
    data object Layout : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route
}

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
    var activeSettingPart by remember { mutableStateOf<TrioSettingsPart?>(null) }

    // The section the page is on *is* the top of the back stack, rather than a
    // second piece of state kept in step with it: the strip and the system back
    // gesture then read and write the same list, so they cannot disagree about
    // where the user is.
    //
    // The supertype is spelled out because the reified parameter is otherwise
    // inferred from the argument, and a stack declared as the first concrete
    // route cannot hold the other three once the process is recreated.
    val nav = rememberNavController<Route>(Route.Layout)
    val openTab = SettingsTab.entries.firstOrNull {
        it.route == nav.backStack.lastOrNull()
    } ?: SettingsTab.Layout
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
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }

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
            nav.popUntil { it == next.route }
        } else {
            nav.push(next.route)
        }
    }

    val restartDone = stringResource(R.string.restart_done)

    // Miuix's own host rather than a system Toast: the snackbar is drawn on the
    // same surface as the page that raised it, so it follows the theme and dies
    // with the screen instead of hovering over whatever comes next.
    //
    // Short (4s) rather than Long (10s): these messages are one short
    // sentence, and ten seconds is a long time to look at one. The user can
    // swipe the bar away if even four is too long.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Remembered so the row callbacks below capture one stable value: a lambda
    // rebuilt every recomposition would make every row that takes it unstable.
    val showMessage: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
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
    // the list would scroll straight through the title.
    val barBlurSupported = isRuntimeShaderSupported()

    // Capture the animated pages for the stationary bottom bar. Each page has a
    // separate content backdrop for its own top bar, keeping sampling acyclic.
    val navBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    CompositionLocalProvider(LocalDropdownBackdrop provides navBackdrop) {
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
                    .kyantLayerBackdrop(navBackdrop),
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
                                    settings = settingsState.value,
                                    onOpenPart = { part -> activeSettingPart = part },
                                )

                                SettingsTab.About -> aboutTab(
                                    settingsState.value, serviceState.value, updater, showMessage,
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
            UpdateNotesDialog(
                info = available?.info?.takeIf { it.version != dismissedUpdateVersion },
                current = available?.current.orEmpty(),
                onDismiss = { dismissedUpdateVersion = available?.info?.version },
                onDownload = { info ->
                    dismissedUpdateVersion = info.version
                    updater.download(info)
                },
            )
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
            SettingsTab.Layout -> R.string.tab_layout
            SettingsTab.Settings -> R.string.tab_settings
            SettingsTab.About -> R.string.about_title
        }
    )
    val restartTitle = stringResource(R.string.restart_title)
    val surfaceColor = MiuixTheme.colorScheme.surface
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
                                        shaderString = PROGRESSIVE_BLUR_ALPHA_MASK_SHADER,
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
                            icon = ImageVector.vectorResource(R.drawable.ic_lucide_rotate_ccw),
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
                .kyantLayerBackdrop(pageBackdrop)
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
private const val PROGRESSIVE_BLUR_ALPHA_MASK_SHADER = """
uniform shader content;
uniform float2 size;
half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return content.eval(coord) * blurAlpha;
}
"""

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

/**
 * The docked bar: Miuix's own [NavigationBar], on the backdrop material.
 *
 * Delegating to the library rather than re-implementing it is what makes this one look
 * like a system control - the item metrics, the selected-label animation and the divider
 * policy all come with it. The only thing added here is the material: vibrancy plus a blur
 * at the user's radius, with NO rim highlight, because a highlight on a bar that is flush
 * with the screen edge would draw a line across the bottom of the display.
 *
 * The surface alpha is much higher than the capsule's (0.58/0.62 against 0.36/0.38). A
 * docked bar sits over the very bottom of the page where there is rarely anything to see
 * through, so a translucent one reads as a dirty smudge rather than as glass.
 */
@Composable
private fun DockedNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val interaction = rememberNavigationDragState(openIndex, SettingsTab.entries.size, onSelect)
    val surface = if (darkTheme) Color.Black.copy(alpha = 0.58f)
    else Color.White.copy(alpha = 0.62f)
    val material = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
            },
            highlight = null,
            onDrawSurface = { drawRect(surface) },
        )
    } else {
        Modifier.background(surface)
    }
    Box(Modifier.fillMaxWidth()) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // The library's bar carries a shadow; over a translucent material it reads
                    // as a dark band along the bottom edge.
                    shape = RectangleShape
                    clip = true
                    shadowElevation = 0f
                    ambientShadowColor = Color.Transparent
                    spotShadowColor = Color.Transparent
                }
                .then(material),
            color = Color.Transparent,
            showDivider = false,
            mode = if (labels == Prefs.NAV_LABELS_ICON) {
                NavigationBarDisplayMode.IconOnly
            } else {
                NavigationBarDisplayMode.IconAndText
            },
        ) {
            SettingsTab.entries.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = index == if (interaction.pressed) interaction.position.roundToInt() else openIndex,
                    onClick = { onSelect(index) },
                    // ImageVector directly, not an `icon = { ... }` slot: Miuix's item takes
                    // the vector and draws it itself, which is what keeps its own sizing and
                    // tinting in charge.
                    icon = iconFor(tab),
                    label = if (labels == Prefs.NAV_LABELS_ICON) "" else stringResource(tab.labelRes),
                )
            }
        }
        Box(Modifier
            .matchParentSize()
            .navigationBarsPadding()
            .navigationDragInput(interaction))
    }
}

/** The icon for one page. Shared by all three bars so they cannot disagree. */
@Composable
private fun iconFor(tab: SettingsTab): ImageVector = when (tab) {
    SettingsTab.Layout -> ImageVector.vectorResource(R.drawable.ic_lucide_circle_dot)
    SettingsTab.Settings -> ImageVector.vectorResource(R.drawable.ic_lucide_sliders_horizontal)
    SettingsTab.About -> ImageVector.vectorResource(R.drawable.ic_lucide_info)
}

/**
 * The floating bar: a capsule inset from the edges, with a rim highlight and a selection
 * that slides between slots.
 *
 * 62% width and 56dp tall, both sampled from the reference. The highlight matters more
 * here than on the docked bar: an inset capsule has an edge all the way round, and without
 * the rim it reads as a grey lozenge rather than as a piece of glass lying on the page.
 */
@Composable
private fun FloatingNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val surface = if (darkTheme) Color.Black.copy(alpha = 0.36f)
    else Color.White.copy(alpha = 0.38f)
    NavigationPill(
        labels = labels,
        openIndex = openIndex,
        darkTheme = darkTheme,
        backdrop = backdrop,
        shape = shape,
        surface = surface,
        widthFraction = 0.78f,
        height = 58.dp,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        chromaticAberration = false,
        onSelect = onSelect,
    )
}

/**
 * The liquid bar: the reference's three-layer composition, which is a different widget from
 * the floating one rather than a variant of it.
 *
 * The layers, and why each exists:
 *
 *   - a 64dp capsule carrying the glass and the content;
 *   - a hidden copy of the row at 56dp, drawn with `alpha = 0` but still *recorded*
 *       into its own layer backdrop, then used as a tint source - that is how the moving
 *       selection picks up the accent colour of whatever is under it;
 *   - the selection itself, sampling the page backdrop AND that hidden copy together, with
 *       a press-progress lens so it refracts while held.
 *
 * Width is 0.53 x 0.82 x 1.10 of the parent, straight from the reference. That number is
 * not arbitrary: the product lands the bar at a width where three tabs still hit a comfortable
 * target, and it is narrower than the floating capsule.
 */
@Composable
private fun LiquidNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    // 0.4 alpha in both themes, and a fixed near-black/near-white rather than the theme's
    // surface: the liquid style is meant to read as a piece of glass with its own tint, not
    // as a lighter patch of the page.
    val surface = if (darkTheme) Color(0xFF121212).copy(alpha = 0.4f)
    else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    NavigationPill(
        labels = labels,
        openIndex = openIndex,
        darkTheme = darkTheme,
        backdrop = backdrop,
        shape = shape,
        surface = surface,
        widthFraction = 0.76f,
        height = 62.dp,
        lensHeight = 24.dp,
        lensAmount = 24.dp,
        chromaticAberration = true,
        liquid = true,
        onSelect = onSelect,
    )
}

/** Floating and liquid bars share input; the liquid selection has its own optical layer. */
@Composable
private fun NavigationPill(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    shape: Shape,
    surface: Color,
    widthFraction: Float,
    height: Dp,
    lensHeight: Dp,
    lensAmount: Dp,
    chromaticAberration: Boolean,
    liquid: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val count = SettingsTab.entries.size
    val interaction = rememberNavigationDragState(openIndex, count, onSelect)
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val accent = MiuixTheme.colorScheme.primary
    val ink = MiuixTheme.colorScheme.onSurface
    val press by animateFloatAsState(
        targetValue = if (interaction.pressed || (liquid && interaction.settling)) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "navigationPress",
    )
    val stretch by animateFloatAsState(
        targetValue = abs(interaction.velocity),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "navigationStretch",
    )
    val panelShift by animateFloatAsState(
        targetValue = if (liquid) with(density) { 4.dp.toPx() } *
                interaction.travel.coerceIn(-1f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "navigationPanelShift",
    )
    val tabsBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val selectionBackdrop = if (liquid && backdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else null
    val baseScale = { if (liquid) 1f + 0.025f * press else 1f - 0.03f * press }
    val material = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
                lens(
                    lensHeight.toPx(), lensAmount.toPx(),
                    depthEffect = true, chromaticAberration = chromaticAberration
                )
            },
            highlight = {
                Highlight.Default.copy(
                    width = 0.75.dp, blurRadius = 0.4.dp,
                    alpha = if (darkTheme) 0.78f else 0.96f
                )
            },
            shadow = null,
            layerBlock = { scaleX = baseScale(); scaleY = baseScale() },
            onDrawSurface = { drawRect(surface) },
            onDrawFront = {
                if (liquid && press > 0f) {
                    val center = Offset(
                        size.width / 2f + direction *
                                (interaction.position - (count - 1) / 2f) * (size.width - 8.dp.toPx()) / count,
                        size.height / 2f,
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(
                                    alpha = 0.16f * press.coerceIn(
                                        0f,
                                        1f
                                    )
                                ), Color.Transparent
                            ),
                            center = center,
                            radius = size.height * 1.5f,
                        )
                    )
                }
            },
        )
    } else {
        Modifier
            .graphicsLayer { scaleX = baseScale(); scaleY = baseScale() }
            .background(surface, shape)
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth(widthFraction)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .height(height),
        ) {
            val slot = (maxWidth - 8.dp) / count
            val slotPx = with(density) { slot.toPx() }
            Box(
                Modifier
                    .matchParentSize()
                    .absoluteOffset { IntOffset(panelShift.roundToInt(), 0) }
                    .shadow(6.dp, shape, clip = false)
                    .then(material))

            if (!liquid) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .absoluteOffset {
                            IntOffset(
                                (direction * (interaction.position - (count - 1) / 2f) * slotPx).roundToInt(),
                                0
                            )
                        }
                        .width(slot)
                        .height(height - 8.dp)
                        .clip(shape)
                        .background(
                            if (darkTheme) Color.White.copy(alpha = 0.18f)
                            else Color.Black.copy(alpha = 0.08f)
                        ))
            }
            NavigationTabRow(
                labels, openIndex, ink,
                Modifier
                    .matchParentSize()
                    .absoluteOffset { IntOffset(panelShift.roundToInt(), 0) }
                    .padding(horizontal = 4.dp),
                onSelect = onSelect,
            )

            if (liquid) {
                if (selectionBackdrop != null) {
                    // Record only the tinted source row. The lens that reads this source
                    // is a sibling, outside the recording, keeping the sampling graph acyclic.
                    Box(
                        Modifier
                            .matchParentSize()
                            .absoluteOffset { IntOffset(panelShift.roundToInt(), 0) }
                            .clearAndSetSemantics { }
                            .graphicsLayer { alpha = 0f }
                            .kyantLayerBackdrop(tabsBackdrop)
                            .then(material)) {
                        NavigationTabRow(
                            labels, openIndex, accent,
                            Modifier
                                .matchParentSize()
                                .padding(horizontal = 4.dp),
                            contentScale = { 1f + 0.2f * press },
                        )
                    }
                }
                val lensScaleX = { (1f + 0.35f * press) * (1f + 0.16f * stretch) }
                val lensScaleY = { (1f + 0.35f * press) * (1f - 0.08f * stretch) }
                val lensMaterial = if (selectionBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = selectionBackdrop,
                        shape = { shape },
                        effects = {
                            val p = press.coerceIn(0f, 1f)
                            lens(
                                (8.dp + 8.dp * p).toPx(), (10.dp + 12.dp * p).toPx(),
                                depthEffect = true, chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(
                                width = 0.8.dp,
                                alpha = 0.25f + 0.65f * press.coerceIn(0f, 1f),
                                style = HighlightStyle.Default(angle = 45f + 40f * interaction.velocity),
                            )
                        },
                        shadow = null,
                        // Use the backdrop's transform hook so refraction stays aligned
                        // with the page while the glass stretches.
                        layerBlock = { scaleX = lensScaleX(); scaleY = lensScaleY() },
                        onDrawSurface = {
                            val p = press.coerceIn(0f, 1f)
                            drawRect((if (darkTheme) Color.White else Color.Black).copy(alpha = 0.10f * (1f - p)))
                            drawRect(Color.White.copy(alpha = 0.035f * p))
                        },
                    )
                } else {
                    Modifier
                        .graphicsLayer { scaleX = lensScaleX(); scaleY = lensScaleY() }
                        .background(accent.copy(alpha = 0.16f), shape)
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .absoluteOffset {
                            IntOffset(
                                (direction * (interaction.position - (count - 1) / 2f) * slotPx + panelShift).roundToInt(),
                                0
                            )
                        }
                        .width(slot)
                        .height(height - 8.dp)
                        .then(lensMaterial))
            }
            // Input stays on the unscaled capsule. Enlarging the glass does not move
            // the hit targets or interfere with the system gesture area below it.
            Box(Modifier
                .matchParentSize()
                .navigationDragInput(interaction))
        }
    }
}

@Composable
private fun NavigationTabRow(
    labels: Int,
    openIndex: Int,
    color: Color,
    modifier: Modifier,
    contentScale: () -> Float = { 1f },
    onSelect: ((Int) -> Unit)? = null,
) {
    Row(modifier.selectableGroup()) {
        SettingsTab.entries.forEachIndexed { index, tab ->
            val label = stringResource(tab.labelRes)
            val showIcon = labels != Prefs.NAV_LABELS_TEXT
            val showLabel = labels != Prefs.NAV_LABELS_ICON
            val action = if (onSelect != null) Modifier.selectable(
                selected = index == openIndex,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = { onSelect(index) },
            ) else Modifier
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(action),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    Modifier.graphicsLayer { scaleX = contentScale(); scaleY = contentScale() },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showIcon) Icon(
                        imageVector = iconFor(tab),
                        contentDescription = if (showLabel) null else label,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    if (showLabel) Text(
                        text = label,
                        color = color,
                        style = TextStyle(
                            fontSize = if (showIcon) 12.3.sp else 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        modifier = if (showIcon) Modifier.padding(top = 1.6.dp) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * A toolbar icon on frosted glass.
 *
 * Sampled from the reference's `GlassToolbarIconButton`. The parts that make it read as
 * glass rather than as a tinted circle:
 *
 *   - the same backdrop stack the bars use (vibrancy, a 2dp blur, lens refraction with
 *     chromatic aberration) drawn under an 80%-alpha surface, so the button shows the page
 *     through the same material instead of approximating it;
 *   - a 0.75dp rim highlight, thin enough to read as an edge rather than as a border;
 *   - a 5dp shadow, which is what lifts it off the bar it sits on;
 *   - its alpha follows `collapsedFraction`, so at rest the button is invisible and it
 *     fades in as the bar thickens. That is the reference's behaviour, and it is why the
 *     expanded bar looks clean rather than cluttered with controls.
 */
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

/**
 * The scrolling body of one destination: the shared header and then the rows of
 * whichever section this is.
 *
 * they belong to. A band pinned above the display would not: it would have to sit
 * outside every destination, which is a different page shape from the one the rows
 * were written for.
 *
 * The rows arrive as a [LazyListScope] builder rather than a composable so each
 * section keeps contributing plain `item {}`s: the list owns their identity and
 * therefore their state, and the shape of the four `*Tab` functions is unchanged.
 *
 * of the back stack. It gates the reset to the first row, so a section opens at the
 * top every time the strip brings it forward rather than resuming where it was left.
 */
@Composable
private fun SectionList(
    contentPadding: PaddingValues,
    isTop: Boolean,
    content: LazyListScope.() -> Unit,
) {
    // A section is a place the reader arrives at, not a document they come back to:
    // opening one starts at its first row. The offset therefore belongs to the visit
    // and is not carried across. Saved page state can restore the old list position,
    // so reset it explicitly on arrival, including an interrupted return transition.
    //
    // Keyed on [isTop] rather than run once, because the same composed list is
    // covered and uncovered as sections change; the reset has to happen on each
    // arrival, not on the composition that happened to create it.
    val listState = rememberLazyListState()
    LaunchedEffect(isTop) {
        if (isTop) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Match the reference's opaque page surface while both pages animate.
            .background(MiuixTheme.colorScheme.surface),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}



@Composable
private fun SettingPreviewCard(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Settings tab displaying concise preview cards that open dedicated sheets on click. */
private fun LazyListScope.settingsTab(
    settings: TrioSettings,
    onOpenPart: (TrioSettingsPart) -> Unit,
) {
    val a = TrioAppearance.of(settings)

    // Card 0: Appearance
    item {
        SettingPreviewCard(
            title = stringResource(R.string.section_appearance),
            summary = stringResource(R.string.card_appearance_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_appearance),
            onClick = { onOpenPart(TrioSettingsPart.Appearance) },
        )
    }

    // Card 1: Battery & Value (Unified)
    item {
        SettingPreviewCard(
            title = stringResource(R.string.card_battery_title),
            summary = stringResource(R.string.card_battery_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_battery),
            onClick = { onOpenPart(TrioSettingsPart.Battery) },
        )
    }

    // Card 2: Wi-Fi Arcs
    item {
        SettingPreviewCard(
            title = stringResource(R.string.card_wifi_title),
            summary = stringResource(R.string.card_wifi_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_wifi),
            onClick = { onOpenPart(TrioSettingsPart.Wifi) },
        )
    }

    // Card 3: Mobile Network & Signal
    item {
        SettingPreviewCard(
            title = stringResource(R.string.card_mobile_title),
            summary = stringResource(R.string.card_mobile_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_signal),
            onClick = { onOpenPart(TrioSettingsPart.Mobile) },
        )
    }

    // Card 4: Network Type
    item {
        SettingPreviewCard(
            title = stringResource(R.string.card_type_title),
            summary = stringResource(R.string.card_type_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_type),
            onClick = { onOpenPart(TrioSettingsPart.Type) },
        )
    }

    // Card 5: Readouts & Performance
    item {
        SettingPreviewCard(
            title = stringResource(R.string.card_readouts_title),
            summary = stringResource(R.string.card_readouts_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_readouts),
            onClick = { onOpenPart(TrioSettingsPart.Readouts) },
        )
    }

    // Card 6: Clock & Date
    item {
        SettingPreviewCard(
            title = stringResource(R.string.card_clock_title),
            summary = stringResource(R.string.card_clock_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_card_clock),
            onClick = { onOpenPart(TrioSettingsPart.Clock) },
        )
    }
}

/** About: activation state, the counters, module info, updates and the log switch. */
private fun LazyListScope.aboutTab(
    settings: TrioSettings,
    service: XposedService?,
    updater: UpdateController,
    showMessage: (String) -> Unit,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    item { ActivatedRow(settings = settings, service = service) }
    item { InfoCard(service = service) }
    item { UpdateCard(updater, showMessage) }
    item { LauncherIconCard() }
    item {
        Card {
            SwitchPreference(
                checked = settings.debugLog,
                onCheckedChange = { value -> update { it.setDebugLog(value) } },
                title = stringResource(R.string.debug_title),
                summary = stringResource(R.string.debug_summary),
                insideMargin = SettingsItemMargin,
            )
        }
    }
    // The shell's own appearance. It lives on the About page rather than the drawing page
    // because it is about this screen, not about the glyph the drawing page edits.
    item {
        Column {
            SectionTitle(stringResource(R.string.nav_section))
            Card {
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
    }
}

/**
 * The release notes in full, after a check turns one up.
 *
 * The card can only show one elided line, and a release's notes are the only thing a user
 * can weigh before committing to an install - so they get a dialog of their own rather than a
 * two-line excerpt. Opened by the check itself, not by a second tap: the answer to "is there a
 * new version" is the notes, and asking the user to go find them is the interruption, not the
 * dialog.
 *
 * @param info the release that was found, or null when there is nothing to show. Passing null
 *   rather than a separate `show` flag keeps the two from disagreeing.
 */
@Composable
private fun UpdateNotesDialog(
    info: UpdateInfo?,
    current: String,
    onDismiss: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
) {
    if (info == null) {
        return
    }
    OverlayDialog(
        show = true,
        title = stringResource(R.string.update_dialog_title, info.version),
        summary = stringResource(
            R.string.update_dialog_summary,
            current,
            formatSize(info.apkSize),
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.update_dialog_notes_title),
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Scrollable and height-capped: release notes run to paragraphs, and an unbounded
            // body would push the buttons off the bottom of the dialog.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val notes = info.notes.trim()
                Text(
                    text = notes.ifEmpty { stringResource(R.string.update_dialog_no_notes) },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.update_dialog_later),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onDownload(info) },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.update_download))
                }
            }
        }
    }
}

/**
 * The in-app updater.
 *
 * The card is a short stack of Miuix preference rows, one per action the
 * A row only exists while its verb is possible, so the card never shows an
 * action that would do nothing, and it never says the same words twice the way
 * a heading plus a button of the same name did.
 *
 * The repository row is the one deliberate exception to that rule, and it
 * sits last so it stays out of the way: reading the source is possible in every
 * a check that just failed.
 *
 * The state lives in [updater], which the screen remembers, so scrolling this
 * card out of view and back does not restart a download in flight.
 */
@Composable
private fun UpdateCard(updater: UpdateController, showMessage: (String) -> Unit) {
    val state = updater.state

    // Null only for Ready, where the row's own title is already the whole status
    // and a summary would print the same sentence a second time.
    val status: String? = when (state) {
        is UpdateState.Idle -> stringResource(R.string.update_summary_idle)
        is UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.UpToDate -> stringResource(R.string.update_uptodate, state.current)
        is UpdateState.NoRelease -> stringResource(R.string.update_no_release)
        is UpdateState.Available -> stringResource(R.string.update_available, state.info.version)
        is UpdateState.Downloading -> stringResource(
            R.string.update_downloading,
            (state.progress * 100f).toInt(),
        )

        is UpdateState.Ready -> null
        
        // would lose its meaning if it were run through a string resource.
        is UpdateState.Failed -> listOfNotNull(
            stringResource(
                if (HAS_REPO) R.string.update_failed else R.string.update_unconfigured,
            ),
            state.detail?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    // A download reports itself under the row it belongs to, where the bar spans
    // the same width as the text instead of sitting in a slot of its own.
    val progressBar: (@Composable () -> Unit)? = if (state is UpdateState.Downloading) {
        {
            LinearProgressIndicator(
                // Null means the server never declared a length: the bar then
                // animates instead of claiming a progress it does not have.
                progress = state.progress.takeIf { it > 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        null
    }

    // Both states that are waiting on the network replace the row's arrow with a
    // spinner, so the row still says "working" once the summary word has stopped
    // being the thing the eye lands on. The branches are written out one by one
    // because the arrow is the default and cannot be restored by passing null.
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading

    // Each row is titled with the verb it performs, so a failure offers "retry"
    // and a download in flight says "download", instead of both calling
    // themselves "check for updates" while doing something else.
    val rowTitle = when (state) {
        is UpdateState.Downloading -> stringResource(R.string.update_download)
        is UpdateState.Failed ->
            stringResource(if (HAS_REPO) R.string.update_retry else R.string.update_title)
        // Not "check for updates": this row no longer offers a check, because a
        // fresh check would drop the download the next row is about to install.
        is UpdateState.Ready -> stringResource(R.string.update_ready)
        else -> stringResource(R.string.update_title)
    }

    Card {
        // The one row that is always there: it reports the state and, whenever
        // nothing is in flight and nothing is waiting to be installed, offers
        // another look at the release feed. It is also the retry action after a
        
        // second row called "retry" to find that out would be one row too many.
        if (busy) {
            ArrowPreference(
                title = rowTitle,
                summary = status,
                insideMargin = SettingsItemMargin,
                enabled = false,
                endActions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 20.dp)
                    }
                },
                bottomAction = progressBar,
            )
        } else if (state is UpdateState.Ready) {
            // Reporting only, and deliberately not tappable: checking again would
            // replace "downloaded" with "up to date" and take the install row down
            // with it, costing the user a second download of a file already on
            // disk. The empty endActions removes the arrow, which would otherwise
            // promise an action this row does not have.
            ArrowPreference(
                title = rowTitle,
                summary = null,
                insideMargin = SettingsItemMargin,
                endActions = {},
            )
        } else if (!HAS_REPO) {
            // No repository in this build: the row states that instead of offering a
            // check that could only ever fail. Still a row rather than nothing, so the
            // card does not silently disappear from the About page.
            ArrowPreference(
                title = stringResource(R.string.update_title),
                summary = stringResource(R.string.update_unconfigured),
                insideMargin = SettingsItemMargin,
                enabled = false,
                endActions = {},
            )
        } else {
            ArrowPreference(
                title = rowTitle,
                summary = status,
                insideMargin = SettingsItemMargin,
                onClick = { updater.check() },
            )
        }

        if (state is UpdateState.Available) {
            // What the install would cost: the version being replaced and the
            // download size, so the choice can be made before tapping.
            val notes = state.info.notes.lines().firstOrNull { it.isNotBlank() }
            // The release notes are the only thing the user can weigh before
            // committing to an install, so a short excerpt earns its place.
            val noteExcerpt: (@Composable () -> Unit)? = if (notes == null) {
                null
            } else {
                {
                    Text(
                        text = notes,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            ArrowPreference(
                title = stringResource(R.string.update_download),
                summary = stringResource(
                    R.string.update_available_summary,
                    state.current,
                    formatSize(state.info.apkSize),
                ),
                insideMargin = SettingsItemMargin,
                bottomAction = noteExcerpt,
                onClick = { updater.download(state.info) },
            )
        }

        if (state is UpdateState.Ready) {
            // Resolved before the click, not inside it: a string resource needs a
            // composable context, and the snackbar is raised from a lambda.
            val refusedHint = stringResource(R.string.update_permission_needed)
            val failedHint = stringResource(R.string.update_install_failed)
            ArrowPreference(
                title = stringResource(R.string.update_install),
                summary = stringResource(R.string.update_ready_summary),
                insideMargin = SettingsItemMargin,
                onClick = {
                    when (updater.install(state.file)) {
                        InstallResult.Started -> Unit

                        // Only this case is the user's to fix, and only the system
                        // settings page can hand over the grant. The message says why
                        // the screen changed under the finger.
                        InstallResult.NeedsPermission -> {
                            showMessage(refusedHint)
                            updater.openInstallPermissionScreen()
                        }

                        // Granting a permission the app already holds would lead to
                        // a settings page with nothing to change.
                        InstallResult.NoInstaller -> showMessage(failedHint)
                    }
                },
            )
        }

        // Both states where the user may want to read more than the card can
        // hold get a way out, and only those states need one.
        if (HAS_REPO && (state is UpdateState.Available || state is UpdateState.Failed)) {
            ArrowPreference(
                title = stringResource(R.string.update_release_page),
                insideMargin = SettingsItemMargin,
                onClick = {
                    updater.openInBrowser(
                        (state as? UpdateState.Available)?.info?.pageUrl ?: RELEASES_PAGE,
                    )
                },
            )
        }

        // Last, and unlike every row above it, always present: the repository is
        
        
        // it away exactly when the card is least useful would be backwards.
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

/**
 * One label/value pair of the about card: a heavy dark label over a lighter grey
 * value, stacked so long values (the framework string, the scope list) stay free
 * to wrap without a second column squeezing them.
 */
@Composable
private fun InfoRow(label: String, value: String, bottomPadding: Dp = 24.dp) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
        text = value,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
    )
}

/**
 * The top block of the about page: a tall "activated" card on the left and two
 * short statistic cards stacked on the right.
 *
 * The size is driven by aspectRatio(1f) rather than a fixed height, matching
 * the reference: the side length follows the available width on any device, so
 * the block keeps its proportions instead of looking squat on a tablet.
 */
@Composable
private fun ActivatedRow(settings: TrioSettings, service: XposedService?) {
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
                label = stringResource(R.string.about_scope_count),
                value = scopeCount(service).toString(),
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

/**
 * The green activation card: state as a heavy title, module version underneath,
 * and the ring-and-check badge bleeding off the bottom-right corner.
 */
@Composable
private fun ActivatedCard(active: Boolean, version: String, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val content = if (dark) MiuixTheme.colorScheme.onSurface else ActivatedContent

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.defaultColors(
            color = if (dark) ActivatedContainerDark else ActivatedContainer,
            contentColor = content,
        ),
        // These cards are read-outs, not buttons: they have nowhere to navigate
        // to, so they stay non-clickable. Miuix wires the press feedback on
        // independently of onClick, which gives the MagicOS "give" under the finger
        // without pretending there is an action behind it. Tilt is the variant
        // the reference page uses on its equivalent cards.
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        // clipToBounds rather than clip(RoundedCornerShape): the card has already
        // drawn its squircle outline, and clipping to a plain rounded rectangle
        // here would square off the corners the user is looking at.
        Box(modifier = Modifier
            .fillMaxSize()
            .clipToBounds()) {
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
            // The badge is not aligned to the corner: it is nudged 27x31 further
            // out so a different slice of the ring survives the clip. The vector
            
            // ring-to-viewport ratio is what the eye compares, and it is easy to
            // get wrong by hand.
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

/** One of the two short right-hand cards: a quiet label over a large figure. */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        // Same read-out-not-a-button treatment as [ActivatedCard]; the two cards
        // sit side by side, so they must not disagree about how they respond.
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

/**
 * The reference's four identity rows first, then the two the reference has no
 * needs to report a bug, so they stay on rather than being dropped for symmetry.
 */
/**
 * The launcher entry, and nothing else.
 *
 * The icon on the home screen is the activity-ALIAS; the settings activity itself
 * advertises the LSPosed entry point. Switching the alias off therefore hides the
 * icon and keeps the page reachable from the manager - which is why this is a
 * component toggle rather than a shortcut removal: it survives reboots, and a
 * launcher that cleans up shortcuts cannot undo it.
 */
@Composable
private fun LauncherIconCard() {
    val context = LocalContext.current
    // Asked of the component on every composition rather than cached: the alias can
    // also be changed by the system (a launcher cleanup, a restore), and a remembered
    // flag would then show a switch that disagrees with the home screen.
    var hidden by remember(context) { mutableStateOf(isLauncherIconHidden(context)) }
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            checked = hidden,
            onCheckedChange = { value ->
                setLauncherIconHidden(context, value)
                hidden = isLauncherIconHidden(context)
            },
            title = stringResource(R.string.about_hide_icon),
            summary = stringResource(R.string.about_hide_icon_summary),
            insideMargin = SettingsItemMargin,
        )
    }
}

/** The alias that carries the launcher intent-filter; see the manifest. */
private const val LAUNCHER_ALIAS = "io.github.odomu.honorduo.ui.MainActivityAlias"

private fun launcherAlias(context: Context): ComponentName =
    ComponentName(context.packageName, LAUNCHER_ALIAS)

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
        // A manager that refuses the write leaves the icon as it was; there is
        // nothing to report and nothing to roll back.
    }
}

@Composable
private fun InfoCard(service: XposedService?) {
    val context = LocalContext.current

    // Every framework getter is a binder call into the daemon: bind them to the
    // service identity so they run when it changes rather than on every
    // recomposition.
    val unknown = stringResource(R.string.info_unknown)
    val module = remember(context) { readModuleInfo(context) }
    val framework = remember(service, unknown) { readFrameworkInfo(service, unknown) }
    val scope = remember(service, unknown) { readScope(service, unknown) }

    // fillMaxWidth is required on both the card and its content column: Miuix's Card
    // wraps to its content, and this card's text is narrower than the sibling cards,
    // so without it the About card renders visibly inset on the right.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoRow(stringResource(R.string.info_system_version), module.systemVersion)
            InfoRow(stringResource(R.string.info_module_version), module.moduleVersion)
            InfoRow(stringResource(R.string.info_framework), framework)
            InfoRow(stringResource(R.string.info_device_model), module.deviceModel)
            InfoRow(stringResource(R.string.info_scope), scope)
            InfoRow(
                label = stringResource(R.string.info_android_version),
                value = module.androidVersion,
                bottomPadding = 12.dp,
            )

            Text(
                text = stringResource(R.string.about_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The confirmation for restarting System UI.
 *
 * [OverlayDialog] rather than a bottom sheet: this is the dialog the colour
 * picker already uses, so the confirmation reads as part of the module instead
 * of borrowing a second popup style. Rendering through the Scaffold's popup host
 * is also why this has to stay inside the Scaffold's content lambda.
 *
 * looking at a root prompt, and closing the dialog there would leave the outcome
 * with nowhere to be reported.
 */
@Composable
private fun RestartDialog(
    controller: RestartController,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val state = controller.state
    val running = state is RestartState.Running

    OverlayDialog(
        show = show,
        title = stringResource(R.string.restart_title),
        summary = stringResource(R.string.restart_summary),
        onDismissRequest = { if (!running) onDismiss() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The raw shell text rides along with the localised headline, the
            // same way a failed update pairs its diagnostic detail.
            if (state is RestartState.Failed) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.restart_failed),
                        state.detail?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { controller.restart() },
                    enabled = !running,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (running) R.string.restart_running else R.string.restart_confirm,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The confirmation for resetting the three role colours.
 *
 * Same [OverlayDialog] shell as the restart confirmation, so it stays inside
 * the Scaffold's content lambda for the same reason.
 */
@Composable
private fun ResetColorsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.color_reset),
        summary = stringResource(R.string.color_reset_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.color_reset_confirm))
            }
        }
    }
}

@Composable
private fun ColorDialog(
    role: RoleColor,
    settings: TrioSettings,
    onPick: (onDark: Boolean, argb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var onDark by remember { mutableStateOf(true) }

    OverlayDialog(
        show = true,
        title = stringResource(role.labelRes),
        summary = stringResource(R.string.color_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundChip(
                    label = stringResource(R.string.color_on_dark),
                    selected = onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = true }
                BackgroundChip(
                    label = stringResource(R.string.color_on_light),
                    selected = !onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = false }
            }
            ColorPalette(
                color = Color(colorFor(settings, role, onDark)),
                onColorChanged = { picked -> onPick(onDark, picked.toArgb()) },
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    text = stringResource(R.string.color_reset_default),
                    onClick = { onPick(onDark, 0) },
                )
            }
        }
    }
}

@Composable
private fun BackgroundChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        )
    }
    Button(onClick = onClick, modifier = modifier, colors = colors) {
        Text(text = label, style = MiuixTheme.textStyles.button)
    }
}

@Composable
internal fun Swatch(color: Int) {
    if (color == 0) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.dp, MiuixTheme.colorScheme.onSurfaceVariantSummary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp, 1.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(color)),
        )
    }
}

/**
 * Integer slider over a closed range, showing the current value at the end.
 *
 * @param step increment between selectable values; the range must divide by it.
 * @param valueTextSuffix appended to the displayed number - "dp" on the sliders
 *   whose setting is a length, so the value text says the unit the summary
 *   talks about instead of a bare figure.
 */
@Composable
internal fun IntSlider(
    value: Int,
    min: Int,
    max: Int,
    title: String,
    summary: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    valueTextSuffix: String? = null,
    onReset: (() -> Unit)? = null,
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    val hasReset = onReset != null && enabled

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SliderPreference(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / step).roundToInt() * step
                onValueChange(snapped.coerceIn(min, max))
            },
            title = title,
            summary = summary,
            valueText = value.toString() + (valueTextSuffix ?: ""),
            enabled = enabled,
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step - 1).coerceAtLeast(0),
            insideMargin = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = if (hasReset) 48.dp else 16.dp,
                bottom = 12.dp,
            ),
        )

        if (hasReset) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 14.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { showResetConfirm = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_rotate_ccw),
                    contentDescription = stringResource(R.string.reset_setting_title),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showResetConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.reset_setting_title),
            summary = stringResource(R.string.reset_setting_confirm, title),
            onDismissRequest = { showResetConfirm = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showResetConfirm = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        showResetConfirm = false
                        onReset?.invoke()
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

/**
 * The stored colour for one role on one background.
 *
 * `internal` because the drawing page's long-press dialog draws the same
 * swatches, and showing a different value there would be showing the wrong colour.
 */
internal fun colorFor(settings: TrioSettings, role: RoleColor, onDark: Boolean): Int =
    when (role) {
        RoleColor.Critical -> if (onDark) settings.criticalOnDark else settings.criticalOnLight
        RoleColor.Low -> if (onDark) settings.lowOnDark else settings.lowOnLight
        RoleColor.Charging -> if (onDark) settings.chargingOnDark else settings.chargingOnLight
        RoleColor.ClockWeekday -> if (onDark) settings.clockWeekdayColorOnDark else settings.clockWeekdayColorOnLight
        RoleColor.ClockDate -> if (onDark) settings.clockDateColorOnDark else settings.clockDateColorOnLight
        RoleColor.ClockLunar -> if (onDark) settings.clockLunarColorOnDark else settings.clockLunarColorOnLight
        RoleColor.HighTemp -> if (onDark) settings.highTempOnDark else settings.highTempOnLight
    }

/** Palette for the activation card, sampled off the reference screenshot. */
private val ActivatedContainer = Color(0xFFE4F9E5)
private val ActivatedContent = Color(0xFF101010)
private val ActivatedBadge = Color(0xFF7FD78A)

/** Dark-mode counterparts; the reference only shows the light theme. */
private val ActivatedContainerDark = Color(0xFF1D2A1F)
private val ActivatedBadgeDark = Color(0xFF6FC77C)

/**
 * True when the app is rendering its dark palette, following the system setting
 * too. Miuix exposes the mode rather than a resolved boolean, so the system case
 * has to be resolved here.
 *
 * `internal` because the drawing page's large preview sits on the same
 * surface and has to follow the same theme, or the two previews would disagree
 * about what the status bar looks like.
 */
@Composable
internal fun isDarkTheme(): Boolean = MiuixTheme.colorSchemeMode == ColorSchemeMode.Dark ||
        (MiuixTheme.colorSchemeMode == ColorSchemeMode.System && isSystemInDarkTheme())

/** Everything the about card renders about this installation. */
private data class ModuleInfo(
    val moduleVersion: String,
    val systemVersion: String,
    val androidVersion: String,
    val deviceModel: String,
)

/**
 * Reads the values that never change while the process lives.
 *
 * [Build.VERSION.INCREMENTAL] and [Build.DISPLAY] are both shown when they differ:
 * on MagicOS the former carries the ROM build (OS4.0.0.27.XNCCNXM) and the latter the
 * underlying AOSP build id, and either one alone leaves the user guessing.
 */
private fun readModuleInfo(context: Context): ModuleInfo {
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
        systemVersion = systemVersion.ifBlank { "?" },
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel = readDeviceName(context),
    )
}

/**
 * The name the user sees in Settings, not the one printed on the box.
 *
 * [Build.MODEL] is the SKU (`23127PN0CC`); the marketing name (`Xiaomi 14`)
 * only exists as [Settings.Global.DEVICE_NAME], and `Build.MARKET_NAME` is not
 * present in this SDK. A device that has never had the setting written falls
 * back to the model rather than showing an empty row.
 */
private fun readDeviceName(context: Context): String {
    val name = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty()
}

/**
 * How many packages the framework will load this module into; the reference page
 * shows an equivalent count, and it is the fastest way to spot a mis-scoped
 * install.
 */
private fun scopeCount(service: XposedService?): Int {
    if (service == null) return 0
    return runCatching { service.scope.size }.getOrDefault(0)
}

/**
 * How many of the appearance switches are on, as a single at-a-glance figure.
 *
 * Derived from [TrioAppearance] rather than read off [TrioSettings] directly.
 * The switches gate one another, and the count used to ignore both the master
 * switch and those gates, so it could report a healthy total while the glyph
 * drew nothing at all.
 */
private fun enabledCount(settings: TrioSettings): Int {
    val a = TrioAppearance.of(settings)
    if (!a.glyph) return 0
    return listOf(
        a.wifi,
        // The reading counts in either position: in the ring it is the dot row,
        // outside it is the bars the out-of-ring view draws.
        a.mobile,
        // The second row only exists when the mobile level is drawn as dots, so
        // out of ring - where nothing is drawn along the bottom at all - it is
        // not a second thing on screen however the switch is set.
        a.dualSim && a.signalDots(),
        a.value,
        a.drawsBolt(),
        // The network type is a three-way mode now; anything but "off" counts as on.
        a.typeAnywhere(),
    ).count { it }
}

/**
 * Every getter here is a binder call into the framework daemon, so all of them are
 * wrapped: a service that died between binding and this call must degrade to
 * [unknown] rather than take the settings screen down with it.
 */
private fun readFrameworkInfo(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    return runCatching {
        val name = service.frameworkName
        val version = service.frameworkVersion
        val code = service.frameworkVersionCode
        "$name $version ($code) API ${service.apiVersion}"
    }.getOrDefault(unknown)
}

private fun readScope(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    val packages = runCatching { service.scope }.getOrNull()
    if (packages.isNullOrEmpty()) return unknown
    return packages.joinToString("\n")
}
