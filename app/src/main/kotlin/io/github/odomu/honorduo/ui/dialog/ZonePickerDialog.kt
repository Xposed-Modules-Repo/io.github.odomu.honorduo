package io.github.odomu.honorduo.ui.dialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.IconZones
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ZoneMove(val part: IconZones.Part, val from: IconZones.Zone)

/**
 * Dialog for choosing which component candidate to add/promote in a zone.
 */
@Composable
fun ZonePickerDialog(
    zone: IconZones.Zone,
    settings: ModuleSettings,
    appearance: StatusAppearance,
    sims: Int,
    currentOccupant: IconZones.Part?,
    onChoose: (IconZones.Part?) -> Unit,
    onEdit: (IconZones.Part) -> Unit,
    onDismiss: () -> Unit,
) {
    val solvable = IconZones.placementChoices(zone, settings, sims)
    val chain = appearance.ringZones[zone.ordinal]
    val canEmpty = IconZones.place(zone, null, settings, sims) != null

    var confirmingMove by remember { mutableStateOf<ZoneMove?>(null) }

    OverlayDialog(
        show = true,
        title = stringResource(
            R.string.drawing_pick_title,
            stringResource(zoneLabelRes(zone)),
            stringResource(occupantLabelRes(currentOccupant)),
        ),
        onDismissRequest = onDismiss,
    ) {
        val windowHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.68f)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
            ) {
                // Emptying first: it is a normal request, and putting it last would push
                // it off the bottom of a long list.
                PickerRow(
                    title = stringResource(R.string.drawing_occupant_none),
                    summary = stringResource(if (canEmpty) R.string.drawing_pick_none_summary else R.string.drawing_clock_fixed),
                    selected = currentOccupant == null,
                    enabled = canEmpty,
                    onClick = { if (canEmpty) onChoose(null) },
                    onLongClick = null,
                )

                IconZones.placeableParts().forEach { part ->
                    val canPlace = solvable.contains(part)
                    val id = IconZones.encodePart(part)
                    val inZone = chain.contains(id)
                    val elsewhere = if (inZone) null else IconZones.Zone.entries.firstOrNull { z ->
                        z != zone && appearance.ringZones[z.ordinal].contains(id)
                    }
                    val reason = when {
                        canPlace -> null
                        !IconZones.accepts(zone, part, settings) -> stringResource(
                            R.string.drawing_pick_signal_in_centre,
                            stringResource(occupantLabelRes(part)),
                        )
                        else -> stringResource(
                            R.string.drawing_pick_impossible,
                            stringResource(zoneLabelRes(zone)),
                        )
                    }
                    val summary = reason ?: when {
                        currentOccupant == part -> stringResource(R.string.drawing_pick_showing_now)
                        inZone -> stringResource(R.string.drawing_pick_promote_summary)
                        elsewhere != null -> stringResource(
                            R.string.drawing_pick_move_summary,
                            stringResource(zoneLabelRes(elsewhere)),
                        )
                        else -> stringResource(partSummaryRes(part))
                    }
                    PickerRow(
                        title = stringResource(occupantLabelRes(part)),
                        summary = summary,
                        selected = currentOccupant == part,
                        inZone = inZone,
                        elsewhere = elsewhere != null,
                        enabled = canPlace,
                        onClick = {
                            if (canPlace) {
                                if (elsewhere != null) {
                                    confirmingMove = ZoneMove(part, elsewhere)
                                } else {
                                    onChoose(part)
                                }
                            }
                        },
                        onLongClick = if (canPlace) {
                            { onEdit(part) }
                        } else {
                            null
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.drawing_close))
                }
            }
        }
    }

    confirmingMove?.let { move ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.drawing_move_title),
            summary = stringResource(
                R.string.drawing_move_summary,
                stringResource(occupantLabelRes(move.part)),
                stringResource(zoneLabelRes(move.from)),
            ),
            onDismissRequest = { confirmingMove = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmingMove = null },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        confirmingMove = null
                        onChoose(move.part)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.drawing_move_confirm))
                }
            }
        }
    }
}

/** One entry in the picker. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickerRow(
    title: String,
    summary: String,
    selected: Boolean,
    inZone: Boolean = false,
    elsewhere: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val content = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> MiuixTheme.colorScheme.primaryContainer
                    inZone -> MiuixTheme.colorScheme.surfaceContainerHigh
                    elsewhere -> MiuixTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                },
            )
            .semantics { this.selected = selected }
            .combinedClickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else content,
                )
                when {
                    inZone && !selected -> Text(
                        text = stringResource(R.string.drawing_pick_in_zone),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    elsewhere -> Text(
                        text = stringResource(R.string.drawing_pick_elsewhere),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        when {
            selected -> Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_check),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            inZone -> Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle_dot),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            elsewhere -> Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_lock),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The short name for a piece, for the zone rows and the picker. */
fun occupantLabelRes(part: IconZones.Part?): Int = when (part) {
    IconZones.Part.WIFI -> R.string.drawing_occupant_wifi
    IconZones.Part.MOBILE -> R.string.drawing_occupant_mobile
    IconZones.Part.VALUE -> R.string.drawing_occupant_value
    IconZones.Part.BOLT -> R.string.drawing_occupant_bolt
    IconZones.Part.TYPE -> R.string.drawing_occupant_type
    IconZones.Part.SIM_1 -> R.string.drawing_occupant_sim_1
    IconZones.Part.SIM_2 -> R.string.drawing_occupant_sim_2
    IconZones.Part.CLOCK -> R.string.drawing_occupant_clock
    IconZones.Part.POWER -> R.string.drawing_occupant_power
    IconZones.Part.TEMP -> R.string.drawing_occupant_temp
    IconZones.Part.RUNNING -> R.string.drawing_occupant_running
    IconZones.Part.FPS -> R.string.drawing_occupant_fps
    IconZones.Part.CURRENT -> R.string.perf_current_title
    IconZones.Part.VOLTAGE -> R.string.perf_voltage_title
    IconZones.Part.SIGNAL -> R.string.perf_signal_title
    IconZones.Part.BT -> R.string.bt_battery_title
    null -> R.string.drawing_occupant_none
}

fun zoneLabelRes(zone: IconZones.Zone): Int = when (zone) {
    IconZones.Zone.TOP -> R.string.drawing_hint_top
    IconZones.Zone.CENTRE -> R.string.drawing_hint_centre
    IconZones.Zone.BOTTOM -> R.string.drawing_hint_bottom
    IconZones.Zone.LEFT -> R.string.drawing_hint_left
    IconZones.Zone.RIGHT -> R.string.drawing_hint_right
}

fun partSummaryRes(part: IconZones.Part): Int = when (part) {
    IconZones.Part.WIFI -> R.string.show_wifi_summary
    IconZones.Part.MOBILE -> R.string.show_mobile_summary
    IconZones.Part.VALUE -> R.string.show_value_summary
    IconZones.Part.BOLT -> R.string.show_bolt_summary
    IconZones.Part.TYPE -> R.string.show_mobile_type_summary
    IconZones.Part.SIM_1 -> R.string.show_sim_1_summary
    IconZones.Part.SIM_2 -> R.string.show_sim_2_summary
    IconZones.Part.CLOCK -> R.string.clock_size_summary
    IconZones.Part.POWER -> R.string.perf_power_summary
    IconZones.Part.TEMP -> R.string.perf_temp_summary
    IconZones.Part.RUNNING -> R.string.perf_running_summary
    IconZones.Part.FPS -> R.string.perf_fps_summary
    IconZones.Part.CURRENT -> R.string.perf_reading_common_summary
    IconZones.Part.VOLTAGE -> R.string.perf_reading_common_summary
    IconZones.Part.SIGNAL -> R.string.perf_reading_common_summary
    IconZones.Part.BT -> R.string.bt_battery_summary
}
