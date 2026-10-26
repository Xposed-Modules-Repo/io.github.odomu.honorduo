package io.github.odomu.honorduo.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.model.StatusState
import io.github.odomu.honorduo.ui.component.BackgroundChip
import io.github.odomu.honorduo.ui.component.IntSlider
import io.github.odomu.honorduo.ui.component.LocalDropdownBackdrop
import io.github.odomu.honorduo.ui.component.SettingsItemMargin
import io.github.odomu.honorduo.ui.component.Swatch
import io.github.odomu.honorduo.ui.control.ContentControlRow
import io.github.odomu.honorduo.ui.control.SettingsPart
import io.github.odomu.honorduo.ui.control.SliderRow
import io.github.odomu.honorduo.ui.control.contentControlsFor
import io.github.odomu.honorduo.ui.control.roleColorsForPart
import io.github.odomu.honorduo.ui.control.slidersForEditor
import io.github.odomu.honorduo.ui.manager.SettingsRepository
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PartSettingsSheet(
    show: Boolean,
    part: SettingsPart,
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    preview: (@Composable () -> Unit)? = null,
    ringSettings: ModuleSettings = settings,
) {
    val a = StatusAppearance.of(settings)
    val rows = slidersForEditor(part)
    val controls = contentControlsFor(part)
    var showDigitColorDialog by remember { mutableStateOf(false) }
    val colors = when (part) {
        SettingsPart.Battery ->
            listOf(RoleColor.Low, RoleColor.Charging, RoleColor.Critical)
        SettingsPart.Readouts ->
            listOf(RoleColor.HighTemp)
        else -> part.part?.let(::roleColorsForPart).orEmpty()
    }
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeight < 600.dp || LocalDensity.current.fontScale > 1.3f
    val maxHeight = windowHeight * 0.72f
    val bottomInset = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val sheetBackdrop = rememberLayerBackdrop()

    OverlayBottomSheet(
        show = show,
        modifier = Modifier.layerBackdrop(sheetBackdrop),
        title = stringResource(
            R.string.drawing_settings_title,
            stringResource(if (part == SettingsPart.Battery && a.ring) R.string.drawing_part_ring else part.labelRes),
        ),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        CompositionLocalProvider(LocalDropdownBackdrop provides sheetBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .padding(bottom = bottomInset),
            ) {
                if (!compact) preview?.invoke()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                ) {
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

                    if (colors.isNotEmpty() && part == SettingsPart.Battery) {
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
                    if (part == SettingsPart.Readouts && settings.perfTemp) {
                        SwitchPreference(
                            checked = a.roleColorsTemp,
                            title = stringResource(R.string.color_role_temp_title),
                            summary = stringResource(R.string.color_role_temp_summary),
                            insideMargin = SettingsItemMargin,
                            onCheckedChange = { value -> update { it.setRoleColorsTemp(value) } },
                        )
                        if (a.roleColorsTemp) {
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
                    }

                    rows.forEach { spec ->
                        if (spec.enabled(a)) {
                            SliderRow(spec, settings, update)
                        }
                    }

                    val showColors = when (part) {
                        SettingsPart.Battery -> a.glyph && a.roleColors
                        SettingsPart.Readouts -> settings.perfTemp && a.roleColorsTemp
                        SettingsPart.Clock, SettingsPart.Bolt -> true
                        else -> false
                    }
                    if (showColors) {
                        if (part == SettingsPart.Clock) {
                            ArrowPreference(
                                title = stringResource(R.string.clock_digit_colors_title),
                                summary = stringResource(R.string.color_field_summary),
                                insideMargin = SettingsItemMargin,
                                onClick = { showDigitColorDialog = true },
                            )
                        }
                        colors.forEach { role ->
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
                    }

                    if (part == SettingsPart.Battery || part == SettingsPart.Clock || part == SettingsPart.Bolt || (part == SettingsPart.Readouts && a.roleColorsTemp)) {
                        ArrowPreference(
                            title = stringResource(R.string.color_reset),
                            summary = stringResource(R.string.color_reset_summary),
                            insideMargin = SettingsItemMargin,
                            onClick = onResetColors,
                        )
                    }
                }
            }

            if (showDigitColorDialog) {
                ClockDigitColorDialog(
                    settings = settings,
                    update = update,
                    onDismiss = { showDigitColorDialog = false },
                )
            }
        }
    }
}

/**
 * Dedicated modal dialog for customizing per-character time colors.
 */
@Composable
internal fun ClockDigitColorDialog(
    settings: ModuleSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var onDark by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val sample = StatusState.sClockBarText.ifEmpty { StatusState.sClockText }.ifEmpty { "19:41:05" }
    val colors = if (onDark) settings.clockDigitColorsOnDark else settings.clockDigitColorsOnLight
    val selectedColor = colors.getOrElse(selected) { 0 }
    val defaultInk = if (onDark) Color.White else Color.Black

    OverlayDialog(
        show = true,
        title = stringResource(R.string.clock_digit_colors_title),
        summary = stringResource(R.string.clock_digit_colors_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundChip(
                    label = stringResource(R.string.color_on_light),
                    selected = !onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = false }
                BackgroundChip(
                    label = stringResource(R.string.color_on_dark),
                    selected = onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = true }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sample.forEachIndexed { index, glyph ->
                        val picked = colors.getOrElse(index) { 0 }
                        val isSelected = index == selected
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    },
                                )
                                .clickable(role = Role.Button) { selected = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = glyph.toString(),
                                style = MiuixTheme.textStyles.headline2,
                                color = if (picked != 0) Color(picked) else defaultInk,
                            )
                        }
                    }
                }
            }

            ColorPalette(
                color = if (selectedColor != 0) Color(selectedColor) else defaultInk,
                onColorChanged = { picked ->
                    update { it.setClockDigitColor(selected, onDark, picked.toArgb()) }
                },
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(R.string.clock_digit_color_clear_one),
                    onClick = {
                        update { it.setClockDigitColor(selected, onDark, 0) }
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.clock_digit_color_clear_all),
                    onClick = {
                        update { it.clearClockDigitColors() }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
