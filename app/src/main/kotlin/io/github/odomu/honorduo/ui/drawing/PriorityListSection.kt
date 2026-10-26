package io.github.odomu.honorduo.ui.drawing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.ui.component.SettingsItemMargin
import io.github.odomu.honorduo.ui.dialog.occupantLabelRes
import io.github.odomu.honorduo.ui.render.IconZones
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PriorityPreferenceRow(
    index: Int,
    part: IconZones.Part,
    lastIndex: Int,
    isDrawing: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
        ) {
            RadioButtonPreference(
                title = stringResource(occupantLabelRes(part)),
                summary = when (index) {
                    0 if isDrawing -> stringResource(R.string.drawing_priority_showing)
                    0 -> stringResource(R.string.drawing_priority_first, 0 + 1)
                    else -> stringResource(R.string.drawing_priority_fallback, index + 1)
                },
                selected = index == 0,
                insideMargin = SettingsItemMargin,
                onClick = onEdit,
            )
        }
        Row(
            modifier = Modifier.padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrderButton(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_up),
                description = R.string.drawing_priority_move_up,
                enabled = index > 0,
            ) { onMove(index, -1) }
            OrderButton(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_down),
                description = R.string.drawing_priority_move_down,
                enabled = index < lastIndex,
            ) { onMove(index, 1) }
            OrderButton(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                description = R.string.drawing_priority_remove,
                enabled = true,
            ) { onRemove() }
        }
    }
}

/**
 * Arrow or remove icon button beside a priority entry row.
 */
@Composable
internal fun OrderButton(
    icon: ImageVector,
    description: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.outline,
            modifier = Modifier.size(15.dp),
        )
    }
}
