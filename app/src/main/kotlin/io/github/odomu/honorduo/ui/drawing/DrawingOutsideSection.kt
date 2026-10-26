package io.github.odomu.honorduo.ui.drawing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.control.SettingsPart
import io.github.odomu.honorduo.ui.screen.PreviewScene
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DrawingOutsideParts(
    settings: ModuleSettings,
    scene: PreviewScene,
    dark: Boolean,
    onEdit: (SettingsPart) -> Unit,
    onReorder: (IntArray) -> Unit,
    renderReadoutSample: @Composable (Modifier) -> Unit,
    renderSmallPreview: @Composable (SettingsPart, Modifier) -> Unit,
) {
    val a = StatusAppearance.of(settings)
    val order = settings.outOrder
    val typeInside = a.stackTypeShown()
    val visible = order.filter { slot ->
        when (slot) {
            Prefs.OUT_SLOT_SIGNAL -> a.signalOutOfRing
            Prefs.OUT_SLOT_READOUTS -> a.perfPairShown()
            Prefs.OUT_SLOT_TYPE -> a.typeOutOfRing && !typeInside &&
                !(a.signalOutOfRing && !a.stackedSignal)
            else -> false
        }
    }.toIntArray()

    if (visible.isEmpty()) return

    val onScreen = visible.reversedArray()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            onScreen.forEachIndexed { screenIndex, slot ->
                val part = settingsPartFor(slot) ?: return@forEachIndexed
                val stored = visible.size - 1 - screenIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button) { onEdit(part) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val previewable = when (part) {
                        SettingsPart.Type -> a.typeOutOfRing
                        else -> true
                    }
                    if (!previewable) {
                        Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.drawing_preview_hidden),
                                style = MiuixTheme.textStyles.footnote2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (part == SettingsPart.Readouts) {
                        renderReadoutSample(Modifier.fillMaxWidth().height(58.dp))
                    } else {
                        renderSmallPreview(part, Modifier.fillMaxWidth().height(58.dp))
                    }
                    Text(
                        text = stringResource(part.labelRes),
                        style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center,
                    )
                    OutOrderArrows(
                        canMoveRight = stored > 0,
                        canMoveLeft = stored < visible.size - 1,
                        onMoveRight = { onReorder(swappedVisible(order, visible, stored, stored - 1)) },
                        onMoveLeft = { onReorder(swappedVisible(order, visible, stored, stored + 1)) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.out_order_outwards),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = stringResource(R.string.out_order_battery),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun OutOrderArrows(
    canMoveRight: Boolean,
    canMoveLeft: Boolean,
    onMoveRight: () -> Unit,
    onMoveLeft: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderButton(
            icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
            description = R.string.out_order_move_out,
            enabled = canMoveLeft,
        ) { onMoveLeft() }
        OrderButton(
            icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
            description = R.string.out_order_move_in,
            enabled = canMoveRight,
        ) { onMoveRight() }
    }
}

fun swappedVisible(order: IntArray, visible: IntArray, from: Int, to: Int): IntArray {
    if (from !in visible.indices || to !in visible.indices) return order.copyOf()
    val next = order.copyOf()
    val first = next.indexOf(visible[from])
    val second = next.indexOf(visible[to])
    if (first < 0 || second < 0) return next
    next[first] = visible[to]
    next[second] = visible[from]
    return next
}

internal fun settingsPartFor(slot: Int): SettingsPart? = when (slot) {
    Prefs.OUT_SLOT_SIGNAL -> SettingsPart.Mobile
    Prefs.OUT_SLOT_READOUTS -> SettingsPart.Readouts
    Prefs.OUT_SLOT_TYPE -> SettingsPart.Type
    else -> null
}
