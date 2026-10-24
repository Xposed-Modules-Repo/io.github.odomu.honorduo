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


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.TrioAppearance
import io.github.odomu.honorduo.model.TrioSettings
import io.github.odomu.honorduo.render.TrioZones
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One switchable component, paired with the title the settings screen shows for
 * it. The drawing page needs the name as well as the identity, because the dialog
 * it opens is titled with it.
 */
internal enum class TrioSettingsPart(val part: TrioZones.Part?, val labelRes: Int) {
    Appearance(null, R.string.drawing_part_appearance),
    Battery(null, R.string.drawing_part_battery),
    Readouts(null, R.string.perf_pair_title),
    Wifi(TrioZones.Part.WIFI, R.string.show_wifi_title),
    /**
     * The signal card, and the one place a per-card row is chosen.
     *
     * SIM 1 and SIM 2 used to be cards of their own, which meant two cards in the
     * component library whose sheets were one shared set of settings - there was no
     * second thing to set, so the second card could only repeat the first. The two
     * cards are one kind of ink (a row of level dots, from one set of geometry
     * constants); what differs between them is which reading they show. So they are
     * rows inside this card instead: "show mobile dots" is the aggregate meter, and
     * the two per-card rows sit under it.
     */
    Mobile(TrioZones.Part.MOBILE, R.string.show_mobile_title),
    Value(TrioZones.Part.VALUE, R.string.show_value_title),
    Bolt(TrioZones.Part.BOLT, R.string.show_bolt_title),
    Type(TrioZones.Part.TYPE, R.string.show_mobile_type_title),
    Clock(TrioZones.Part.CLOCK, R.string.drawing_occupant_clock),
    ;

    companion object {
        /**
         * The entry for `part`, or null when it has no row of its own.
         *
         * SIM 1 and SIM 2 have no card of their own any more, so they resolve to the
         * signal card: tapping either card's ink on the preview opens the one sheet that
         * offers both switches.
         */
        fun of(part: TrioZones.Part?): TrioSettingsPart? = when (part) {
            null -> null
            TrioZones.Part.SIM_1, TrioZones.Part.SIM_2 -> Mobile
            else -> entries.firstOrNull { it.part == part }
        }
    }
}

/**
 * The sections of the size page. The drawing page's per-part dialog reuses the
 * same rows, so the two places that can change a size share one definition or
 * they drift - which is the failure this file exists to prevent.
 */
internal enum class GeometryGroup(val titleRes: Int) {
    Scale(R.string.group_scale),
    Stroke(R.string.group_stroke),
    Text(R.string.group_text),
    Type(R.string.group_type),
    OutSignal(R.string.group_out_signal),
    Layout(R.string.group_layout),
    Track(R.string.group_track),
}

/**
 * Font weights snap in hundreds: 100, 200, ... 900.
 *
 * Lives here rather than on the settings screen because the weight rows do: the
 * three sliders that use it are rows of [geometrySliders], and a step restated at
 * each of them is three copies of one number. It was `WEIGHT_STEP` beside the old
 * hand-written `geometryTab`, which left it dead the moment the rows moved here.
 */
private const val WEIGHT_STEP = 100

/** Animation speed moves in tens: 50, 60, ... 200. Finer than that is unfeelable. */
private const val SPEED_STEP = 10

/**
 * One size row, described once.
 *
 * Every field is a function of the appearance rather than a captured value,
 * because several rows rename themselves for the rectangular arrangement and
 * every row's gate has to follow the current snapshot.
 *
 * @param part which component this row sizes, or null when it belongs to the
 *   glyph as a whole rather than to one switchable piece. The drawing page's
 *   long-press dialog filters on this.
 */
internal class SliderSpec(
    val group: GeometryGroup,
    val part: TrioZones.Part?,
    val min: Int,
    val max: Int,
    /**
     * The ceiling for the current appearance, when it is not a constant.
     *
     * Only the percentage uses it: a placed reading in the ring is sized by the third it
     * sits in, which can carry far more than the fixed slots of the capsule and rectangle.
     * Defaults to [max], so every other row keeps its constant span.
     */
    val maxFor: (TrioAppearance) -> Int = { max },
    val read: (TrioSettings) -> Int,
    val write: (SettingsRepository, Int) -> Unit,
    val titleRes: (TrioAppearance) -> Int,
    val summaryRes: (TrioAppearance) -> Int,
    val enabled: (TrioAppearance) -> Boolean,
    /** The gates whose titles explain a greyed-out row, in reading order. */
    val step: Int = 1,
    val suffix: String? = null,
    val editorOwner: TrioSettingsPart = TrioSettingsPart.Appearance,
)

/**
 * Every size row in the module, as one table.
 *
 * Both the size page and the drawing page's part dialog render from this list,
 * so a new slider is added here rather than in two places. The order within a
 * group is the order the size page shows.
 *
 * The gates are the rule the whole settings screen follows: a row may be gated
 * by *other* switches but never by its own value, or switching it off would
 * disable the row and it could never be turned back on. See the note on the
 * percentage-weight row, which is gated on the percentage switch rather than on
 * the rule that decides whether a percentage is drawn.
 */
internal fun geometrySliders(): List<SliderSpec> {
    val value = TrioZones.Part.VALUE
    val wifi = TrioZones.Part.WIFI
    val mobile = TrioZones.Part.MOBILE
    val type = TrioZones.Part.TYPE
    val bolt = TrioZones.Part.BOLT
    return listOf(
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_SIZE,
            max = Prefs.MAX_CLOCK_SIZE,
            maxFor = { if (it.capsule) Prefs.MAX_CAPSULE_READING_SIZE else Prefs.MAX_CLOCK_SIZE },
            read = { it.clockSize },
            write = { r, v -> r.setClockSize(v) },
            titleRes = { R.string.clock_size_title },
            summaryRes = { R.string.clock_size_summary },
            enabled = { true },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_WEIGHT,
            max = Prefs.MAX_CLOCK_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.clockWeight },
            write = { r, v -> r.setClockWeight(v) },
            titleRes = { R.string.clock_weight_title },
            summaryRes = { R.string.clock_weight_summary },
            enabled = { true },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_OFFSET,
            max = Prefs.MAX_CLOCK_OFFSET,
            read = { it.clockTopOffsetX },
            write = { r, v -> r.setClockTopOffsetX(v) },
            titleRes = { R.string.clock_top_offset_x_title },
            summaryRes = { R.string.clock_top_offset_x_summary },
            enabled = { true },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_OFFSET,
            max = Prefs.MAX_CLOCK_OFFSET,
            read = { it.clockBottomOffsetX },
            write = { r, v -> r.setClockBottomOffsetX(v) },
            titleRes = { R.string.clock_bottom_offset_x_title },
            summaryRes = { R.string.clock_bottom_offset_x_summary },
            enabled = { true },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_ROW_GAP,
            max = Prefs.MAX_CLOCK_ROW_GAP,
            read = { it.clockRowGap },
            write = { r, v -> r.setClockRowGap(v) },
            titleRes = { R.string.clock_row_gap_title },
            summaryRes = { R.string.clock_row_gap_summary },
            enabled = { true },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = TrioZones.Part.CLOCK,
            min = Prefs.MIN_CLOCK_LUNAR_GAP,
            max = Prefs.MAX_CLOCK_LUNAR_GAP,
            read = { it.clockLunarGap },
            write = { r, v -> r.setClockLunarGap(v) },
            titleRes = { R.string.clock_lunar_gap_title },
            summaryRes = { R.string.clock_lunar_gap_summary },
            enabled = { it.clockLunar },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_GLYPH_SCALE,
            max = Prefs.MAX_GLYPH_SCALE,
            read = { it.glyphScale },
            write = { r, v -> r.setGlyphScale(v) },
            titleRes = { R.string.glyph_scale_title },
            summaryRes = { R.string.glyph_scale_summary },
            // Gated on the master switch and nothing else: it sizes the glyph itself, so with the glyph off there is nothing for it to act on.
            enabled = { true },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_PERF_SCALE,
            max = Prefs.MAX_PERF_SCALE,
            read = { it.perfScale },
            editorOwner = TrioSettingsPart.Readouts,
            write = { r, v -> r.setPerfScale(v) },
            titleRes = { R.string.perf_scale_title },
            summaryRes = { R.string.perf_scale_summary },
            // Gated on the two readout switches rather than on their own values: a reading that is not drawn has no size to set.
            enabled = { it.perfPairShown() },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_PERF_WEIGHT,
            max = Prefs.MAX_PERF_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.perfWeight },
            editorOwner = TrioSettingsPart.Readouts,
            write = { r, v -> r.setPerfWeight(v) },
            titleRes = { R.string.perf_weight_title },
            summaryRes = { R.string.perf_weight_summary },
            enabled = { it.perfPairShown() },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = null,
            min = Prefs.MIN_PERF_LINE_GAP,
            max = Prefs.MAX_PERF_LINE_GAP,
            read = { it.perfLineGap },
            editorOwner = TrioSettingsPart.Readouts,
            write = { r, v -> r.setPerfLineGap(v) },
            titleRes = { R.string.perf_line_gap_title },
            summaryRes = { R.string.perf_line_gap_summary },
            enabled = { it.perfPairShown() },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = null,
            min = Prefs.MIN_RING_STROKE,
            max = Prefs.MAX_RING_STROKE,
            read = { it.ringStroke },
            editorOwner = TrioSettingsPart.Battery,
            write = { r, v -> r.setRingStroke(v) },
            // One stroke setting, two names: the rectangle spends it on the bar's
            // thickness, the ring on the outline's width.
            titleRes = { if (it.rect) R.string.stroke_title_rect else R.string.stroke_title },
            summaryRes = { if (it.rect) R.string.stroke_summary_rect else R.string.stroke_summary },
            enabled = { true },
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = wifi,
            min = Prefs.MIN_WIFI_SCALE,
            max = Prefs.MAX_WIFI_SCALE,
            read = { it.wifiScale },
            write = { r, v -> r.setWifiScale(v) },
            titleRes = { R.string.wifi_scale_title },
            summaryRes = { R.string.wifi_scale_summary },
            enabled = { true },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = wifi,
            min = Prefs.MIN_ARC_STROKE,
            max = Prefs.MAX_ARC_STROKE,
            read = { it.arcStroke },
            write = { r, v -> r.setArcStroke(v) },
            titleRes = { R.string.arc_stroke_title },
            // In the rectangle the arc group hangs off the top slot by its own
            // stroke, so this slider moves the arcs as well as thickening them.
            summaryRes = {
                if (it.rect) R.string.arc_stroke_summary_rect else R.string.arc_stroke_summary
            },
            enabled = { true },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = value,
            min = Prefs.MIN_VALUE_SIZE,
            max = Prefs.MAX_VALUE_SIZE,
            // The ring's slots are sized by the third each piece sits in, so a placed reading has more room than the capsule's or the rectangle's fixed slot.
            maxFor = {
                when {
                    it.ring -> Prefs.MAX_RING_VALUE_SIZE
                    it.capsule -> Prefs.MAX_CAPSULE_READING_SIZE
                    else -> Prefs.MAX_VALUE_SIZE
                }
            },
            read = { it.valueSize },
            write = { r, v -> r.setValueSize(v) },
            titleRes = { R.string.value_size_title },
            summaryRes = { R.string.value_size_summary },
            enabled = { true },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = value,
            min = Prefs.MIN_VALUE_WEIGHT,
            max = Prefs.MAX_VALUE_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.valueWeight },
            write = { r, v -> r.setValueWeight(v) },
            titleRes = { R.string.value_weight_title },
            summaryRes = { R.string.value_weight_summary },
            // Gated on the percentage switch, deliberately not on drawsBolt() or any rule that folds this row's own value in.
            enabled = { it.value },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = bolt,
            min = Prefs.MIN_BOLT_SCALE,
            max = Prefs.MAX_BOLT_SCALE,
            read = { it.boltScale },
            write = { r, v -> r.setBoltScale(v) },
            titleRes = { R.string.bolt_scale_title },
            summaryRes = { R.string.bolt_scale_summary },
            // Gated on the bolt's own switch ("show a bolt while charging"), not on drawsBolt().
            enabled = { it.boltWanted },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = bolt,
            min = Prefs.MIN_BOLT_SPEED,
            max = Prefs.MAX_BOLT_SPEED,
            step = SPEED_STEP,
            read = { it.boltSpeed },
            write = { r, v -> r.setBoltSpeed(v) },
            titleRes = { R.string.bolt_speed_title },
            summaryRes = { R.string.bolt_speed_summary },
            // Only while there is an animation to speed up: a speed for a motion that is
            // switched off is not a choice, so the row is absent rather than greyed out.
            enabled = { it.boltAnimates() },
            suffix = "%",
        ),
        /**
         * The ring's level-dot size - one row for both the top and the bottom row.
         *
         * Owned by [TrioZones.Part.MOBILE] because that is the card the dot ink belongs
         * to, and the two per-card rows share it: the ink is one kind of thing (a row of
         * four beads) and the geometry that sizes it is one set of constants, so a second
         * slider for the cards would be two names for one bead.
         *
         * Gated on the arrangement rather than on a switch. The row sizes ink that only
         * the ring has an opening for; the capsule's rows fit their own outline and the
         * rectangle's columns are square, so on those two the setting would do nothing and
         * a live slider that does nothing is worse than a greyed one that says why. It is
         * not gated on the mobile switches either - a user who has just turned the dots off
         * still needs to set the size before turning them back on, which is the same rule
         * every other size row follows.
         */
        SliderSpec(
            group = GeometryGroup.Type,
            part = mobile,
            min = Prefs.MIN_DOT_SCALE,
            max = Prefs.MAX_DOT_SCALE,
            read = { it.dotScale },
            write = { r, v -> r.setDotScale(v) },
            titleRes = { R.string.dot_scale_title },
            summaryRes = { R.string.dot_scale_summary },
            enabled = { it.ring },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_SIZE,
            max = Prefs.MAX_TYPE_SIZE,
            maxFor = { if (it.capsule) Prefs.MAX_CAPSULE_READING_SIZE else Prefs.MAX_TYPE_SIZE },
            read = { it.typeSize },
            write = { r, v -> r.setTypeSize(v) },
            titleRes = { R.string.type_size_title },
            summaryRes = { R.string.type_size_summary },
            enabled = { it.typeInRing },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_SIZE_DP,
            max = Prefs.MAX_OUT_TYPE_SIZE_DP,
            read = { it.outTypeSize },
            write = { r, v -> r.setOutTypeSize(v) },
            titleRes = { R.string.out_type_size_title },
            summaryRes = { R.string.out_type_size_summary },
            enabled = { it.typeOutOfRing },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_SUFFIX_SCALE,
            max = Prefs.MAX_TYPE_SUFFIX_SCALE,
            read = { it.typeSuffixScale },
            write = { r, v -> r.setTypeSuffixScale(v) },
            titleRes = { R.string.type_suffix_scale_title },
            summaryRes = { R.string.type_suffix_scale_summary },
            // Both the standalone label and the caption inside the reading shrink it.
            // A property of the label itself rather than of either position, so
            // "a network type is drawn somewhere" is the right gate.
            enabled = { it.typeAnywhere() },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_MARGIN,
            max = Prefs.MAX_OUT_TYPE_MARGIN,
            read = { it.outTypeMarginLeft },
            write = { r, v -> r.setOutTypeMarginLeft(v) },
            titleRes = { R.string.out_type_margin_left_title },
            summaryRes = { R.string.out_type_margin_left_summary },
            // Only the standalone label has margins to set; in "above" mode the caption
            // is drawn inside the reading and these do nothing.
            enabled = { it.typeOutOfRing && it.stackLabel == Prefs.STACK_LABEL_ALONE },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_MARGIN,
            max = Prefs.MAX_OUT_TYPE_MARGIN,
            read = { it.outTypeMarginRight },
            write = { r, v -> r.setOutTypeMarginRight(v) },
            titleRes = { R.string.out_type_margin_right_title },
            summaryRes = { R.string.out_type_margin_right_summary },
            enabled = { it.typeOutOfRing && it.stackLabel == Prefs.STACK_LABEL_ALONE },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_WEIGHT,
            max = Prefs.MAX_TYPE_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.typeWeight },
            write = { r, v -> r.setTypeWeight(v) },
            titleRes = { R.string.type_weight_title },
            summaryRes = { R.string.type_weight_summary },
            enabled = { it.typeAnywhere() },
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_SIGNAL_SIZE,
            max = Prefs.MAX_OUT_SIGNAL_SIZE,
            read = { it.outSignalSize },
            write = { r, v -> r.setOutSignalSize(v) },
            titleRes = { R.string.out_signal_size_title },
            summaryRes = { R.string.out_signal_size_summary },
            // The size of the out-of-ring reading only exists while that reading does, so
            // both halves of the choice gate the row: the position and the stacked switch.
            enabled = { it.stackedOut() },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_DOT_SIZE,
            max = Prefs.MAX_OUT_DOT_SIZE,
            read = { it.outDotSize },
            write = { r, v -> r.setOutDotSize(v) },
            titleRes = { R.string.out_dot_size_title },
            summaryRes = { R.string.out_dot_size_summary },
            enabled = { it.stackedOut() },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_SIGNAL_MARGIN,
            max = Prefs.MAX_OUT_SIGNAL_MARGIN,
            read = { it.outSignalMargin },
            write = { r, v -> r.setOutSignalMargin(v) },
            titleRes = { R.string.out_signal_margin_title },
            summaryRes = { R.string.out_signal_margin_summary },
            enabled = { it.stackedOut() },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_CAP_GAP,
            max = Prefs.MAX_CAP_GAP,
            read = { it.capGap },
            write = { r, v -> r.setCapGap(v) },
            titleRes = { R.string.cap_gap_title },
            summaryRes = { R.string.cap_gap_summary },
            // Only the capsule has cells to separate.
            enabled = { it.capsule },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_CAPSULE_LENGTH,
            max = Prefs.MAX_CAPSULE_LENGTH,
            read = { it.capsuleLength },
            write = { r, v -> r.setCapsuleLength(v) },
            titleRes = { R.string.capsule_length_title },
            summaryRes = { R.string.capsule_length_summary },
            enabled = { it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE_X,
            max = Prefs.MAX_CAPSULE_NUDGE_X,
            read = { it.capsuleLeftDx },
            write = { r, v -> r.setCapsuleLeftDx(v) },
            titleRes = { R.string.capsule_nudge_left_dx_title },
            summaryRes = { R.string.capsule_nudge_left_dx_summary },
            enabled = { it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE_Y,
            max = Prefs.MAX_CAPSULE_NUDGE_Y,
            read = { it.capsuleLeftDy },
            write = { r, v -> r.setCapsuleLeftDy(v) },
            titleRes = { R.string.capsule_nudge_left_dy_title },
            summaryRes = { R.string.capsule_nudge_left_dy_summary },
            enabled = { it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE_X,
            max = Prefs.MAX_CAPSULE_NUDGE_X,
            read = { it.capsuleRightDx },
            write = { r, v -> r.setCapsuleRightDx(v) },
            titleRes = { R.string.capsule_nudge_right_dx_title },
            summaryRes = { R.string.capsule_nudge_right_dx_summary },
            enabled = { it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_CAPSULE_NUDGE_Y,
            max = Prefs.MAX_CAPSULE_NUDGE_Y,
            read = { it.capsuleRightDy },
            write = { r, v -> r.setCapsuleRightDy(v) },
            titleRes = { R.string.capsule_nudge_right_dy_title },
            summaryRes = { R.string.capsule_nudge_right_dy_summary },
            enabled = { it.capsule },
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_RING_SPACING_X,
            max = Prefs.MAX_RING_SPACING_X,
            read = { it.ringSpacingX },
            editorOwner = TrioSettingsPart.Appearance,
            write = { r, v -> r.setRingSpacingX(v) },
            titleRes = { R.string.ring_spacing_x_title },
            summaryRes = { R.string.ring_spacing_x_summary },
            enabled = { it.ring },
            suffix = "px",
        ),
        SliderSpec(
            group = GeometryGroup.Layout,
            part = null,
            min = Prefs.MIN_RING_SPACING_Y,
            max = Prefs.MAX_RING_SPACING_Y,
            read = { it.ringSpacingY },
            editorOwner = TrioSettingsPart.Appearance,
            write = { r, v -> r.setRingSpacingY(v) },
            titleRes = { R.string.ring_spacing_y_title },
            summaryRes = { R.string.ring_spacing_y_summary },
            enabled = { it.ring },
            suffix = "px",
        ),
        SliderSpec(
            group = GeometryGroup.Track,
            part = null,
            min = Prefs.MIN_TRACK_ALPHA,
            max = Prefs.MAX_TRACK_ALPHA,
            read = { it.trackAlpha },
            editorOwner = TrioSettingsPart.Battery,
            write = { r, v -> r.setTrackAlpha(v) },
            titleRes = { R.string.track_alpha_title },
            summaryRes = { R.string.track_alpha_summary },
            enabled = { true },
        ),
    )
}

/** The rows that size one component, for the drawing page's long-press dialog. */
internal fun slidersForPart(part: TrioZones.Part): List<SliderSpec> =
    geometrySliders().filter { it.part == part }

internal fun slidersForEditor(part: TrioSettingsPart): List<SliderSpec> = when (part) {
    TrioSettingsPart.Appearance, TrioSettingsPart.Battery, TrioSettingsPart.Readouts ->
        geometrySliders().filter {
            (it.part == null && it.editorOwner == part) ||
                (part == TrioSettingsPart.Battery && it.part == TrioZones.Part.VALUE)
        }
    // Each card gets its own rows. For the signal card those are the out-of-ring readings' two sizes plus the in-ring dot size.
    else -> part.part?.let(::slidersForPart).orEmpty()
}
/**
 * The role colours a component's ink takes, so the dialog can offer them.
 *
 * Only two pieces are coloured by role: the percentage takes the low and
 * critical colours, and the bolt takes the charging one. The arcs and the level
 * dots follow the status bar's foreground, so they have no colour of their own to
 * offer and say so instead of showing a row that would do nothing.
 */
internal fun roleColorsForPart(part: TrioZones.Part): List<RoleColor> = when (part) {
    TrioZones.Part.VALUE -> listOf(RoleColor.Low, RoleColor.Critical)
    TrioZones.Part.BOLT -> listOf(RoleColor.Charging)
    TrioZones.Part.CLOCK -> listOf(RoleColor.ClockWeekday, RoleColor.ClockDate, RoleColor.ClockLunar)
    else -> emptyList()
}

/**
 * The long-press dialog: the rows that size the tapped component, plus the role
 * colours it takes, both editable in place.
 *
 * It renders [SliderSpec]s from the same table the size page uses, so a change
 * to a row's range, gate or wording lands in both places at once. The rows keep
 * their own gates rather than being hidden: a greyed-out row that names what is
 * holding it shut tells the user why the value they came to change has no effect,
 * where a missing row would leave them wondering whether they had opened the wrong
 * dialog. A component with no size and no colour of its own says so outright.
 *
 * The colour rows reuse the page's own picker rather than a second one, which
 * is why [onEditColor] is a callback: the picker is a dialog of its own and two
 * dialogs stacked is a state the page already models, so the parent keeps owning
 * it.
 */
@Composable
internal fun PartSettingsSheet(
    show: Boolean,
    part: TrioSettingsPart,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    preview: (@Composable () -> Unit)? = null,
    ringSettings: TrioSettings = settings,
) {
    val a = TrioAppearance.of(settings)
    val rows = slidersForEditor(part)
    val controls = contentControlsFor(part)
    val colors = when (part) {
        TrioSettingsPart.Battery ->
            listOf(RoleColor.Low, RoleColor.Charging, RoleColor.Critical)
        TrioSettingsPart.Readouts ->
            listOf(RoleColor.HighTemp)
        else -> part.part?.let(::roleColorsForPart).orEmpty()
    }
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeight < 600.dp || LocalDensity.current.fontScale > 1.3f
    val maxHeight = windowHeight * 0.72f
    // The system's gesture bar (or nav bar) overlays the bottom of the window, so the sheet's last row has to stay above it.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val sheetBackdrop = rememberLayerBackdrop()

    OverlayBottomSheet(
        show = show,
        // Recorded on the sheet itself, not on the content inside it.
        modifier = Modifier.layerBackdrop(sheetBackdrop),
        title = stringResource(R.string.drawing_settings_title,
            stringResource(if (part == TrioSettingsPart.Battery && a.ring) R.string.drawing_part_ring else part.labelRes)),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        CompositionLocalProvider(LocalDropdownBackdrop provides sheetBackdrop) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)
                    .padding(bottom = bottomInset),
            ) {
                if (!compact) preview?.invoke()
                Column(modifier = Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    if (compact) preview?.invoke()
                    controls.forEach { control -> ContentControlRow(control, settings, update, ringSettings) }
                if (rows.isEmpty() && colors.isEmpty() && controls.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawing_settings_none),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                // Colour rows follow the same rule as everything else: no master
                // switch, no rows at all.
                if (colors.isNotEmpty() && part == TrioSettingsPart.Battery) {
                    SwitchPreference(
                        checked = a.roleColors,
                        title = stringResource(R.string.color_role_title),
                        insideMargin = SettingsItemMargin,
                        onCheckedChange = { value -> update { it.setRoleColors(value) } },
                    )
                    if (a.roleColors) {
                        IntSlider(
                            value = settings.lowThreshold,
                            min = Prefs.MIN_LOW_THRESHOLD,
                            max = Prefs.MAX_LOW_THRESHOLD,
                            title = stringResource(R.string.low_threshold_title),
                            summary = stringResource(R.string.low_threshold_summary),
                            enabled = true,
                            onValueChange = { value -> update { it.setLowThreshold(value) } },
                            onReset = { update { it.setLowThreshold(Prefs.DEF_LOW_THRESHOLD) } },
                        )
                    }
                }
                if (part == TrioSettingsPart.Readouts && settings.perfTemp) {
                    IntSlider(
                        value = settings.tempThreshold,
                        min = Prefs.MIN_TEMP_THRESHOLD,
                        max = Prefs.MAX_TEMP_THRESHOLD,
                        title = stringResource(R.string.temp_threshold_title),
                        summary = stringResource(R.string.temp_threshold_summary),
                        valueTextSuffix = "℃",
                        enabled = true,
                        onValueChange = { value -> update { it.setTempThreshold(value) } },
                        onReset = { update { it.setTempThreshold(Prefs.DEF_TEMP_THRESHOLD) } },
                    )
                }

                rows.forEach { spec ->
                    // A row whose setting is off is not shown at all, not greyed.
                    if (spec.enabled(a)) {
                        SliderRow(spec, settings, update)
                    }
                }

                if ((a.glyph && a.roleColors) || part == TrioSettingsPart.Clock || part == TrioSettingsPart.Readouts || part == TrioSettingsPart.Bolt) colors.forEach { role ->
                    ArrowPreference(
                        title = stringResource(role.labelRes),
                        summary = stringResource(R.string.color_field_summary),
                        insideMargin = SettingsItemMargin,
                        startAction = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Swatch(colorFor(settings, role, onDark = true))
                                Swatch(colorFor(settings, role, onDark = false))
                            }
                        },
                        onClick = { onEditColor(role) },
                    )
                }

                if (part == TrioSettingsPart.Battery || part == TrioSettingsPart.Clock || part == TrioSettingsPart.Bolt) {
                    ArrowPreference(
                        title = stringResource(R.string.color_reset),
                        summary = stringResource(R.string.color_reset_summary),
                        insideMargin = SettingsItemMargin,
                        onClick = onResetColors,
                    )
                }
            }
            }
        }
    }
}

/** One size row, rendered from its spec. Shared by the page and this dialog. */
@Composable
internal fun SliderRow(
    spec: SliderSpec,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    IntSlider(
        value = spec.read(settings).coerceAtMost(spec.maxFor(a)),
        min = spec.min,
        max = spec.maxFor(a),
        step = spec.step,
        title = stringResource(spec.titleRes(a)),
        summary = stringResource(spec.summaryRes(a)),
        enabled = spec.enabled(a),
        valueTextSuffix = spec.suffix,
        onValueChange = { v -> update { r -> spec.write(r, v) } },
        onReset = {
            val defVal = spec.read(TrioSettings.defaults())
            update { r -> spec.write(r, defVal) }
        },
    )
}

