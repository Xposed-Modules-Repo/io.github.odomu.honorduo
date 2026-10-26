/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.ui.manager.RestartController
import io.github.odomu.honorduo.ui.manager.RestartState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Confirmation dialog before restarting SystemUI to apply deeper hook changes.
 */
@Composable
internal fun RestartDialog(
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
