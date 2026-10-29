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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.control.ContentControl
import io.github.odomu.honorduo.ui.control.ContentControlRow
import io.github.odomu.honorduo.ui.control.SettingsPart
import io.github.odomu.honorduo.ui.dialog.PartSettingsSheet
import io.github.odomu.honorduo.ui.dialog.RoleColor
import io.github.odomu.honorduo.ui.dialog.ZonePickerDialog
import io.github.odomu.honorduo.ui.dialog.occupantLabelRes
import io.github.odomu.honorduo.ui.dialog.zoneLabelRes
import io.github.odomu.honorduo.ui.drawing.DrawingOutsideParts
import io.github.odomu.honorduo.ui.drawing.PriorityPreferenceRow
import io.github.odomu.honorduo.ui.drawing.SceneChip
import io.github.odomu.honorduo.ui.drawing.SceneSelector
import io.github.odomu.honorduo.ui.drawing.SignalOutOfRingNotice
import io.github.odomu.honorduo.ui.manager.SettingsRepository
import io.github.odomu.honorduo.ui.render.IconZones
import io.github.odomu.honorduo.ui.view.StatusPreviewView
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val REMOVE = 0

/**
 * The drawing page: the glyph rendered large, with its three parts tappable.
 *
 * This is the page that answers "which switch draws the thing I am looking
 * at". Everywhere else in this app the settings are a list of switches named after
 * pieces; here the pieces are the interface, and a switch is what a tap on one
 * does.
 *
 * It is a page of its own rather than a mode of the General tab because it
 * needs room: the targets are thirds of the glyph, so the glyph has to be large
 * enough that a finger can land inside one third on purpose. The General tab's
 * preview strip is about forty dp a cell - a comparison aid, not an input surface.
 *
 * <h2>What a tap does</h2>
 *
 * Tap a third and a list opens of the components that could be drawn there; pick
 * one and it is placed, displacing whatever was there. The list is not a fixed table -
 * it is whatever [IconZones.choices] reports as placeable in that zone, and choosing
 * an entry writes the switch combination [IconZones.assign] solved for. Both live in
 * `IconZones` rather than here because they have to agree with where the pieces
 * are actually drawn, and both are checked against real rendered pixels by
 * `tests/drawing-zones.ps1`.
 *
 * The first entry is "nothing here", which empties the zone.
 *
 * Long-press on a row opens that component's own size and colour settings, from
 * the same row table the size page renders, so the two places cannot offer
 * different ranges for one setting.
 */

/** Row rhythm inside the editor card, matching the rest of the settings screen. */
private val DrawingItemMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp)

/**
 * The battery and signal states the preview can be put into.
 *
 * This exists because three of the six pieces are only reachable in a state the
 * phone is not usually in: the charging bolt needs a charger, the low and critical
 * colours need a low battery, and the dual rows need two answered SIMs. Without a
 * way to ask for those, those pieces would have no tappable presence at all on
 * this page.
 *
 * Each carries the whole state rather than a flag, because they are mutually
 * exclusive: a frame cannot be both charging and critical, and modelling them as
 * independent toggles would allow combinations the renderer has no look for.
 */
internal enum class PreviewScene(
    val labelRes: Int,
    val level: Int,
    val charging: Boolean = false,
    val quickCharging: Boolean = false,
    val powerSave: Boolean = false,
    val low: Boolean = false,
    val wifiLevel: Int = WIFI_ON_LEVEL,
    val slotLevels: IntArray? = null,
) {
    Normal(R.string.drawing_scene_normal, 64),
    Charging(R.string.drawing_scene_charging, 88, charging = true),
    QuickCharging(R.string.drawing_scene_quick, 88, charging = true, quickCharging = true),
    Low(R.string.drawing_scene_low, 24, powerSave = true, low = true),
    Critical(R.string.drawing_scene_critical, 12, low = true),
    NoWifi(R.string.drawing_scene_no_wifi, 79, wifiLevel = NO_WIFI_LEVEL),
}

/** The Wi-Fi level the preview shows: mid-strength, so arcs are drawn. */
private const val WIFI_ON_LEVEL = 3

/** What the renderer reads as "no Wi-Fi ink": the percentage drops to the centre. */
private const val NO_WIFI_LEVEL = -1

/** Mobile level the preview shows: full, so the meter is legible. */
private const val MOBILE_LEVEL = 4

/**
 * Per-SIM levels, so the dual reading draws two rows of different heights.
 *
 * SIM 1 one step down from SIM 2, matching the settings screen's own preview
 * strip. Equal levels would make the dual switch invisible in the rectangular
 * arrangement, where the reading only changes which levels the two columns carry.
 */
private val PREVIEW_SLOTS = intArrayOf(2, 4)

/** The same synthetic SIM readings feed the renderer and the placement solver. */
private val PreviewScene.previewSlots: IntArray
    get() = slotLevels ?: PREVIEW_SLOTS

/**
 * The network-type label the preview stands in for the real one.
 *
 * Deliberately the "5GA" form rather than "5G": the trailing A is what the
 * suffix-scale row changes, so it is the only form in which that setting can be
 * seen at all.
 */
private const val PREVIEW_TYPE = "5GA"

/** Last placement, so a runtime override can be explained beside the preview. */
private data class ZoneSelection(val zone: IconZones.Zone, val part: IconZones.Part)

/**
 * The drawing page's body.
 *
 * @param settings the live snapshot, read at composition so a write is visible on
 *   the next frame: the page and the glyph have to agree about what is switched on,
 *   and the glyph is drawn from this same object.
 */
internal fun LazyListScope.drawingTab(
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    /**
     * The stored snapshot for a given arrangement, used by the style tiles.
     *
     * A plain lambda rather than handing the whole repository down: the page only ever
     * needs to READ one other snapshot, and a read accessor is what keeps that dependency
     * from turning into "the page may write anything".
     */
    snapshotForStyle: (Int) -> ModuleSettings,
) {
    item {
        DrawingEditorCard(
            settings = settings,
            update = update,
            onEditColor = onEditColor,
            onResetColors = onResetColors,
            snapshotForStyle = snapshotForStyle,
        )
    }
}

/**
 * The whole editor: what each third holds, the large glyph, and the state selector.
 *
 * [settings] is the live snapshot rather than a local copy - a tap calls
 * straight through to the repository and the parent recomposes with the new value -
 * so there is no second model here to fall out of step with what the status bar
 * will draw.
 */
@Composable
private fun DrawingEditorCard(
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    snapshotForStyle: (Int) -> ModuleSettings,
) {
    // Which component's settings dialog is open, if any.
    var editing by remember { mutableStateOf<SettingsPart?>(null) }
    var sheetVisible by remember(editing) { mutableStateOf(editing != null) }
    var pendingColor by remember { mutableStateOf<RoleColor?>(null) }
    var pendingReset by remember { mutableStateOf(false) }
    // The zone whose picker is open, if any. One at a time: the picker is a dialog,
    // and two stacked pickers would be a state with no sensible dismissal order.
    var picking by remember { mutableStateOf<IconZones.Zone?>(null) }
    // The third under the finger, so the guide can outline what a press would open.
    var pressed by remember { mutableStateOf<IconZones.Zone?>(null) }
    var scene by rememberSaveable { mutableStateOf(PreviewScene.Normal) }
    var previewTheme by rememberSaveable { mutableIntStateOf(0) }
    var lastSelection by remember { mutableStateOf<ZoneSelection?>(null) }

    val appearance = StatusAppearance.of(settings)
    val systemDark = isSystemInDarkTheme()
    val dark = when (previewTheme) { 1 -> false; 2 -> true; else -> systemDark }

    // This page previews synthetic readings, independently of the switch being
    // edited. Counting the switch would make enabling the second row unreachable.
    val sims = scene.previewSlots.count { it >= 0 }
    val ringSettings = IconZones.editableRingSettings(settings, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims)
    // Which cell the page-level tabs show. Kept in the page rather than in a dialog so the
    // glyph above stays visible while the order is edited - which is the point of editing it.
    var openZone by remember { mutableStateOf(IconZones.Zone.CENTRE) }
    val chooseZone: (IconZones.Zone) -> Unit = { zone ->
        // One path for every zone now.
        openZone = zone
        pressed = zone
        picking = zone
    }
    /**
     * Edit one zone's priority list.
     *
     * [delta] is `-1`/`+1` to move an entry up or down, and `0` to drop
     * that single entry. Dropping is deliberately not "clear the zone": when the head is
     * removed the candidate behind it takes over, which is the whole point of a fallback
     * chain.
     */
    val reorderZone: (IconZones.Zone, Int, Int) -> Unit = { zone, index, delta ->
        // A stale row (the list shrank since it was drawn) resolves to a no-op rather than
        // writing a list built from an index that no longer exists.
        val next = if (delta == 0) {
            IconZones.withoutAt(settings, zone.ordinal, index)
        } else {
            IconZones.moveWithin(settings, zone.ordinal, index, delta)
        }
        if (next !== settings) {
            update { r -> applyZoneAssignment(next, r, settings) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DrawingSection(R.string.section_feature_switches, PaddingValues(0.dp)) {
            ContentControlRow(ContentControl.Enabled, settings, update)
            ContentControlRow(ContentControl.MergeIcons, settings, update)
            ContentControlRow(ContentControl.AntiBurnIn, settings, update)
            ContentControlRow(ContentControl.EffectiveScope, settings, update)
        }

        DrawingSection(R.string.section_merge_style) {
            DrawingStyleSelector(
                settings = settings,
                scene = scene,
                dark = dark,
                snapshotForStyle = snapshotForStyle,
                onSelect = { style ->
                    lastSelection = null
                    update { it.setTrioStyle(style) }
                },
            )
            Text(
                text = stringResource(R.string.drawing_state_title),
                style = MiuixTheme.textStyles.subtitle,
            )
            SceneSelector(
                selected = scene,
                onSelect = { scene = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(listOf(R.string.drawing_theme_system, R.string.drawing_theme_light,
                    R.string.drawing_theme_dark).withIndex().toList(), key = { it.index }) { entry ->
                    SceneChip(stringResource(entry.value), previewTheme == entry.index) {
                        previewTheme = entry.index
                    }
                }
            }

            DrawingGlyph(
                settings = settings,
                scene = scene,
                dark = dark,
                pressed = pressed,
                onPressChange = { pressed = it },
                // A press on the glyph opens that zone's picker.
                onEditZone = { zone ->
                    val part = IconZones.occupant(
                        zone, appearance, scene.level, scene.charging,
                        scene.wifiLevel, PREVIEW_TYPE, sims,
                    )
                    if (part == null) {
                        picking = zone
                    } else {
                        editing = SettingsPart.of(part)
                    }
                },
                onEditBattery = { editing = SettingsPart.Battery },
                onEditAppearance = { editing = SettingsPart.Appearance },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            if (!appearance.enabled) {
                Text(
                    text = stringResource(R.string.drawing_master_off),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else if (!appearance.mergeIcons) {
                Text(
                    text = stringResource(R.string.drawing_merge_off),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        DrawingSection(R.string.drawing_order_title) {
            // The strip lists the zones this arrangement actually has.
            val zones = zonesFor(appearance)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                zones.forEach { zone ->
                    val selected = zone == openZone
                    val text = stringResource(zoneLabelRes(zone))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) MiuixTheme.colorScheme.surface
                                else Color.Transparent
                            )
                            .clickable {
                                openZone = zone
                                pressed = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val chain = appearance.ringZones[openZone.ordinal]
            // Shown whenever the signal is out of ring, not only when a signal row happens to be in this zone's list.
            if (appearance.signalOutOfRing) {
                SignalOutOfRingNotice()
            }
            val actual = IconZones.occupant(
                openZone, appearance, scene.level, scene.charging,
                scene.wifiLevel, PREVIEW_TYPE, sims,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (chain.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawing_priority_empty_summary),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
                chain.forEachIndexed { index, id ->
                    val part = IconZones.decodePart(id) ?: return@forEachIndexed
                    PriorityPreferenceRow(
                        index = index,
                        part = part,
                        lastIndex = chain.size - 1,
                        isDrawing = part == actual,
                        onMove = { i, delta -> reorderZone(openZone, i, delta) },
                        onRemove = { reorderZone(openZone, index, REMOVE) },
                        // The same sheet the library opens, so the two entrances cannot
                        // disagree about what a piece's settings are.
                        onEdit = { SettingsPart.of(part)?.let { editing = it } },
                    )
                }
            }

            lastSelection?.let { selection ->
                PlacementFeedback(selection, appearance, scene, sims)
            }

            Button(
                onClick = { picking = openZone },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.drawing_priority_add))
            }
        }

        DrawingSection(R.string.drawing_outside_title) {
            DrawingOutsideParts(
                settings = settings,
                scene = scene,
                dark = dark,
                onEdit = { editing = it },
                onReorder = { order -> update { it.setOutOrder(order) } },
                renderReadoutSample = { modifier -> DrawingReadoutSample(settings, dark, modifier) },
                renderSmallPreview = { part, modifier ->
                    val a = StatusAppearance.of(settings)
                    DrawingSmallPreview(
                        settings, scene, dark, modifier,
                        outType = part == SettingsPart.Type && a.typeOutOfRing,
                        outSignal = part == SettingsPart.Mobile && a.stackedOut(),
                    )
                },
            )
        }
    }

    val pickZone = picking
    if (pickZone != null) {
        val actual = IconZones.occupant(
            pickZone, appearance, scene.level, scene.charging,
            scene.wifiLevel, PREVIEW_TYPE, sims,
        )
        ZonePickerDialog(
            zone = pickZone,
            settings = settings,
            appearance = appearance,
            sims = sims,
            currentOccupant = actual,
            onChoose = { part ->
                val next = IconZones.withPiece(settings, pickZone.ordinal, part)
                update { r -> applyZoneAssignment(next, r, settings) }
                lastSelection = part?.let { ZoneSelection(pickZone, it) }
                picking = null
            },
            onEdit = { part ->
                picking = null
                editing = SettingsPart.of(part)
            },
            onDismiss = { picking = null },
        )
    }

    val current = editing
    if (current != null) {
        PartSettingsSheet(
            show = sheetVisible,
            part = current,
            settings = settings,
            ringSettings = ringSettings,
            update = update,
            // The colour picker is the page's own, opened over this dialog.
            onEditColor = { role ->
                pendingColor = role
                sheetVisible = false
            },
            onResetColors = {
                pendingReset = true
                sheetVisible = false
            },
            preview = {
                Column {
                    DrawingSettingsPreview(current, settings, scene, dark)
                    SceneSelector(scene, { scene = it }, Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp))
                }
            },
            onDismiss = { sheetVisible = false },
            onDismissFinished = {
                editing = null
                pendingColor?.let { role ->
                    pendingColor = null
                    onEditColor(role)
                }
                if (pendingReset) {
                    pendingReset = false
                    onResetColors()
                }
            },
        )
    }
}

/** A labelled card using the same section rhythm as the settings page. */
@Composable
private fun DrawingSection(
    titleRes: Int,
    contentPadding: PaddingValues = DrawingItemMargin,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(
            text = stringResource(titleRes),
            modifier = Modifier.padding(top = 4.dp),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        )
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
}

private fun drawingPartLabel(part: SettingsPart): Int = when (part) {
    SettingsPart.Appearance -> R.string.drawing_part_appearance
    SettingsPart.Readouts -> R.string.drawing_part_readouts
    else -> part.part?.let(::occupantLabelRes) ?: part.labelRes
}

@Composable
private fun DrawingStyleSelector(
    settings: ModuleSettings, scene: PreviewScene, dark: Boolean,
    snapshotForStyle: (Int) -> ModuleSettings, onSelect: (Int) -> Unit,
) {
    val styles = listOf(Prefs.STYLE_RING, Prefs.STYLE_CAPSULE)
    val labels = listOf(R.string.trio_style_ring, R.string.trio_style_capsule)
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        styles.forEachIndexed { index, style ->
            val chosen = settings.trioStyle == style
            val sample = remember(style, settings) {
                // The live snapshot IS this style's snapshot while it is the chosen one;
                // reading it back out of the preferences costs a full store read on every
                // settings write for no change.
                if (style == settings.trioStyle) {
                    settings
                } else {
                    settings.withComponentsFrom(snapshotForStyle(style), style)
                }
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (chosen) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh)
                .border(if (chosen) 2.dp else 0.dp, if (chosen) MiuixTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .semantics { selected = chosen }
                .clickable(enabled = settings.enabled, role = Role.RadioButton) { onSelect(style) }
                .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DrawingSmallPreview(sample, scene, dark, Modifier.fillMaxWidth().height(64.dp))
                Text(stringResource(labels[index]), style = MiuixTheme.textStyles.footnote1,
                    color = if (chosen) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun DrawingSmallPreview(
    settings: ModuleSettings, scene: PreviewScene, dark: Boolean, modifier: Modifier,
    outType: Boolean = false, outSignal: Boolean = false,
) {
    AndroidView(modifier = modifier, factory = { StatusPreviewView(it) }, update = { view ->
        view.setSettings(settings)
        view.setOutTypeOnly(outType)
        view.setOutSignalOnly(outSignal)
        view.setHighlight(null)
        view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
        view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        view.setState(scene.level, scene.charging, scene.quickCharging, scene.powerSave,
            scene.low, scene.wifiLevel, MOBILE_LEVEL, scene.previewSlots, PREVIEW_TYPE)
    })
}

/**
 * The sample at the top of a component's settings sheet.
 *
 * Both branches clip to the same shape as the cards this sheet is made of: the preview
 * is an `AndroidView` that paints its own background, and a Compose `clip`
 * alone does not reach inside it - the view rounds its own corners, and this keeps the
 * Compose layer from showing a square edge underneath the 12dp it rounds to.
 */
@Composable
internal fun DrawingSettingsPreview(
    part: SettingsPart, settings: ModuleSettings, scene: PreviewScene, dark: Boolean,
) {
    val a = StatusAppearance.of(settings)
    val shape = RoundedCornerShape(12.dp)
    if (part == SettingsPart.Readouts) {
        DrawingReadoutSample(settings, dark, Modifier.fillMaxWidth().height(92.dp))
        Text(stringResource(R.string.drawing_readout_sample_summary),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(vertical = 4.dp))
    } else {
        // A piece that only exists in one state is previewed in that state.
        val sample = if (part == SettingsPart.Bolt && !scene.charging) {
            PreviewScene.Charging
        } else {
            scene
        }
        // A released piece is previewed on its own, not inside the glyph.
        val released = releasedPart(part, settings)
        if (released != 0) {
            DrawingPartPreview(settings, released, dark,
                Modifier.fillMaxWidth().height(92.dp).clip(shape))
            return
        }
        DrawingSmallPreview(
            settings, sample, dark,
            Modifier.fillMaxWidth().height(92.dp).clip(shape),
            outType = part == SettingsPart.Type && a.typeOutOfRing,
            // The cell follows what the signal actually is this frame: the stacked reading only exists out of ring with its switch on.
            outSignal = part == SettingsPart.Mobile && a.stackedOut(),
        )
    }
}

/**
 * The piece a sheet has to preview on its own, or `0` when the arrangement still draws it.
 *
 * Only Wi-Fi and the clock can be released to the system without being switched off, and
 * only they have anything to tune while released.
 */
private fun releasedPart(part: SettingsPart, settings: ModuleSettings): Int {
    val piece = part.part ?: return 0
    val id = when (piece) {
        IconZones.Part.WIFI -> Prefs.PART_WIFI
        IconZones.Part.CLOCK -> Prefs.PART_CLOCK
        // The type has no placement to return from: outside IS its released position,
        // and that is exactly when its own sample should be shown on its own.
        IconZones.Part.TYPE -> {
            val a = StatusAppearance.of(settings)
            return if (a.typeOutOfRing) Prefs.PART_TYPE else 0
        }
        else -> return 0
    }
    return if (StatusAppearance.of(settings).hasRingPart(id)) 0 else id
}

/**
 * One released component, drawn alone.
 *
 * The same view and renderer as every other cell; only the draw call differs, so the
 * size and weight shown are the ones the status bar will use once it is placed again.
 */
@Composable
private fun DrawingPartPreview(
    settings: ModuleSettings, part: Int, dark: Boolean, modifier: Modifier,
) {
    AndroidView(modifier = modifier, factory = { StatusPreviewView(it) }, update = { view ->
        view.setSettings(settings)
        view.setOutTypeOnly(false)
        view.setOutSignalOnly(false)
        view.setPartOnly(part)
        view.setHighlight(null)
        view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
        view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        view.setState(
            64, false,
            quickCharging = false,
            powerSave = false,
            low = false,
            wifiLevel = WIFI_ON_LEVEL,
            mobileLevel = MOBILE_LEVEL,
            slotLevels = PREVIEW_SLOTS,
            // The type sample needs the label it draws; the other released pieces do not.
            mobileType = if (part == Prefs.PART_TYPE) PREVIEW_TYPE else null,
        )
    })
}

/**
 * A live sample of the two performance readouts.
 *
 * The sample always shows a value for every readout that is switched on, even when the
 * current preview scene has nothing to report - the power reading only exists while
 * charging, and the default scene is not charging, so keying the sample to the scene left
 * this cell permanently blank.
 *
 * The numbers (`9.6W`, `32°C`) are fixed stand-ins: a sample of the type, not a claim
 * about the device.
 *
 * **One fixed size, and the text is always whole.** The sample is not a measurement of
 * the status bar - the status bar is live, so a size change is visible there the moment the
 * slider moves. What this cell has to do is show *which pieces* are in the strip and
 * *roughly how they sit together*, so it draws one legible size and lets the longest
 * line decide it. Deriving the size from `outTypeSize` was tried and rejected: it made this
 * cell disagree with the network-type cell next to it, and at the sizes that fit the height
 * the text ran past the width and was silently clipped - "9.6W" showed as "9.6".
 *
 * The weight is the reading's own, because that one is worth previewing: it is the only
 * property that changes the shape of the text rather than just its size.
 */
@Composable
private fun DrawingReadoutSample(settings: ModuleSettings, dark: Boolean, modifier: Modifier) {
    val a = StatusAppearance.of(settings)
    // With neither switch on there is nothing to report, but the cell still has to show the type it sizes.
    val readings = listOfNotNull(
        if (settings.perfPower) "9.6W" else null,
        if (settings.perfTemp) "32°C" else null,
    ).ifEmpty { listOf("32°C") }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (dark) Color(0xFF1C1B1F) else Color(0xFFF2F2F7)), contentAlignment = Alignment.Center) {
        // Fit the cell on both axes, measured rather than estimated.
        val longest = readings.maxByOrNull { it.length }.orEmpty()
        val probe = if (longest.isEmpty()) {
            null
        } else {
            textMeasurer.measure(
                text = longest,
                style = TextStyle(
                    fontSize = 100.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight(a.perfTypeWeight()),
                ),
                maxLines = 1,
            )
        }
        // 100sp was the trial size, so a single glyph-run's width per unit of font size is
        // its measured width over 100. Margins keep the text clear of the rounded corners.
        val widthPerUnit = probe?.let { with(density) { it.size.width.toDp().value } / 100f } ?: 0f
        val byWidth = if (widthPerUnit > 0f) maxWidth.value * 0.86f / widthPerUnit else 0f
        val byHeight = maxHeight.value * 0.82f / (readings.size * 1.35f)
        val size = minOf(
            if (byWidth > 0f) byWidth else byHeight,
            byHeight,
        )
        Text(readings.joinToString("\n"), color = if (dark) Color.White else Color.Black,
            fontSize = with(density) { size.dp.toSp() },
            lineHeight = with(density) { (size * 1.3f).dp.toSp() },
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight(a.perfTypeWeight()), textAlign = TextAlign.Center)
    }
}

/**
 * The three out-of-ring pieces, each as a live sample.
 *
 * A cell shows "hidden" only when the piece *cannot* be previewed at all - the
 * network type when it is set to draw inside the ring. A switch that is merely off is not
 * a reason to blank the cell: these samples exist so the size and weight rows have
 * something to act on, and a user who has just switched a reading off still needs to see
 * what it will look like before switching it back on. The two readouts already say so
 * themselves, in [DrawingReadoutSample]'s own empty state.
 */
/**
 * The three out-of-ring pieces, positioned the way the status bar actually lays them out.
 *
 * **The row runs left to right and the RIGHTmost tile is the one next to the battery.**
 * That is not a cosmetic choice: `layoutOutRingStrip` measures every slot from the
 * battery outwards, so the stored order's first entry is the piece nearest the battery and
 * therefore the rightmost one on screen in LTR. Drawing the list left to right in stored
 * order - which is what this card first did - showed the arrangement mirrored, so the arrows
 * moved a piece the opposite way from the tile the user pressed.
 *
 * The stored array stays in "nearest the battery first" order, because that is what the
 * layout consumes. Only the *rendering* is reversed, and only here: making the storage
 * order screen order would push the mirror image into every other reader.
 *
 * **The tiles are ordered, and that is the control.** Showing the samples in the shipped
 * order would make the setting invisible: the user would move a piece, read "first", and still
 * be looking at the same three tiles.
 *
 * The order is also the row's own layout order, so what the user arranges here is
 * what the status bar draws, nearest the battery last.
 */
/** Explain the saved component using the same runtime frame as the visible glyph. */
@Composable
private fun PlacementFeedback(
    selection: ZoneSelection,
    appearance: StatusAppearance,
    scene: PreviewScene,
    sims: Int,
) {
    val actual = IconZones.occupant(
        selection.zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    if (actual == selection.part) return

    val messageRes = when {

        scene.charging && selection.part != IconZones.Part.BOLT -> R.string.drawing_feedback_charging
        selection.part == IconZones.Part.BOLT && !scene.charging -> R.string.drawing_feedback_bolt
        selection.part == IconZones.Part.WIFI && scene.wifiLevel < 0 -> R.string.drawing_feedback_wifi
        else -> R.string.drawing_feedback_other
    }
    Text(
        text = stringResource(
            messageRes,
            stringResource(occupantLabelRes(selection.part)),
            stringResource(zoneLabelRes(selection.zone)),
            stringResource(occupantLabelRes(actual)),
        ),
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Writes the switch combination already solved for the selected zone and component.
 *
 * The combination is solved by [IconZones.assign] against the real arbitration
 * rule, so nothing here decides which switches to move. That is deliberate: this
 * page asks for an outcome ("put the number in the middle") and the renderer decides
 * what that costs, which is the only arrangement that cannot drift.
 *
 * Only the switches that differ are written, and they are written through the same
 * repository setters the rest of the screen uses - so the local commit, the push to
 * the hooked process and the broadcast all happen exactly once per changed switch.
 */
private fun applyZoneAssignment(
    next: ModuleSettings,
    repository: SettingsRepository,
    settings: ModuleSettings,
) {
    // The slot-driven arrangements write the whole layout in one transaction.
    if (IconZones.hasSlots(next)) {
        repository.applyRingAssignment(next)
        return
    }
    if (next.enabled != settings.enabled) repository.setEnabled(next.enabled)
    if (next.mergeIcons != settings.mergeIcons) repository.setMergeIcons(next.mergeIcons)
    if (next.signalMode != settings.signalMode) repository.setSignalMode(next.signalMode)
    if (next.showWifi != settings.showWifi) repository.setShowWifi(next.showWifi)
    if (next.showMobile != settings.showMobile) repository.setShowMobile(next.showMobile)
    if (next.showValue != settings.showValue) repository.setShowValue(next.showValue)
    if (next.showBolt != settings.showBolt) repository.setShowBolt(next.showBolt)
    if (next.dualSim != settings.dualSim) repository.setDualSim(next.dualSim)
    if (next.valueCentred != settings.valueCentred) {
        repository.setValueCentred(next.valueCentred)
    }
    if (next.mobileTypeMode != settings.mobileTypeMode) {
        repository.setMobileTypeMode(next.mobileTypeMode)
    }
}

/**
 * The list of pieces that can be put in one zone.
 *
 * Every entry comes from [IconZones.choices], which answers by asking the solver
 * whether each piece is placeable - so the menu and the solver cannot disagree. A
 * piece the zone cannot hold at all is still listed, greyed out with the reason,
 * rather than hidden: a missing entry leaves the user wondering whether they opened
 * the wrong list, where a disabled one tells them why.
 *
 * "Nothing here" is the first entry, because emptying a zone is a normal thing to
 * want and burying it at the bottom would push it out of sight on a long list.
 */

/**
 * The zones are resolved by the *view*, because it is what knows the
 * transform the glyph is drawn with: the gestures here ask it for the third under
 * the finger rather than working one out from the touch coordinates. That is the
 * whole design of this composable - a second copy of the design-to-view mapping on
 * the Compose side is exactly how a hot spot ends up beside the piece it names.
 *
 * The gestures are Compose's rather than a touch listener on the view, and that
 * is a deliberate correction: a listener returning true for `ACTION_DOWN` swallows
 * the event, so the page could not be scrolled by dragging over a glyph that fills
 * most of the screen. [detectTapGestures] instead gives up the moment the finger
 * moves past the touch slop, so a drag scrolls the list and a press that stays put
 * is a tap.
 *
 * The handler reads its callbacks through [rememberUpdatedState] so the detector,
 * which is keyed on the settings rather than rebuilt per frame, always calls the
 * current lambda; one captured at composition would close over an older snapshot
 * and flip a switch based on what was on screen several edits ago.
 */
@Composable
private fun DrawingGlyph(
    settings: ModuleSettings,
    scene: PreviewScene,
    dark: Boolean,
    pressed: IconZones.Zone?,
    onPressChange: (IconZones.Zone?) -> Unit,
    onEditZone: (IconZones.Zone) -> Unit,
    onEditBattery: () -> Unit,
    onEditAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPress = rememberUpdatedState(onPressChange)
    val currentEdit = rememberUpdatedState(onEditZone)
    val currentBattery = rememberUpdatedState(onEditBattery)
    val currentAppearance = rememberUpdatedState(onEditAppearance)

    // The view the gestures interrogate. A plain holder rather than a MutableState.
    val holder = remember { arrayOfNulls<StatusPreviewView>(1) }
    // Resolved against the view that is actually laid out, so a gesture arriving
    // before the first layout finds no zone instead of guessing one.
    val zoneAt: (Offset) -> IconZones.Zone? = { offset ->
        holder[0]?.zoneAt(offset.x, offset.y)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(settings, scene) {
                detectTapGestures(
                    onPress = { offset ->
                        currentPress.value(if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true)
                            null else zoneAt(offset))
                        // Waits for the press to resolve, which is also what lets a
                        // drag cancel this detector and be handled as a scroll.
                        tryAwaitRelease()
                        currentPress.value(null)
                    },
                    onTap = { _ ->
                        currentAppearance.value()
                    },
                    onLongPress = { offset ->
                        if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true) currentBattery.value()
                        else zoneAt(offset)?.let { currentEdit.value(it) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            // Fills the square Box above; the aspect ratio belongs to the Box so the
            // gesture detector covers exactly the area the glyph is drawn in.
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                StatusPreviewView(context).also { holder[0] = it }
            },
            update = { view ->
                view.setSettings(settings)
                view.setOutTypeOnly(false)
                view.setOutSignalOnly(false)
                view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
                view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                view.setHighlight(pressed)
                view.setState(
                    scene.level, scene.charging, scene.quickCharging, scene.powerSave,
                    scene.low, scene.wifiLevel, MOBILE_LEVEL,
                    scene.previewSlots, PREVIEW_TYPE,
                )
            },
        )
    }
}

// Natural in-ring tab order: Left, Top, Centre, Bottom, Right
private fun zonesFor(a: StatusAppearance): List<IconZones.Zone> = listOf(
    IconZones.Zone.LEFT,
    IconZones.Zone.TOP,
    IconZones.Zone.CENTRE,
    IconZones.Zone.BOTTOM,
    IconZones.Zone.RIGHT,
)
