package io.github.odomu.honorduo.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.component.BackgroundChip
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

enum class RoleColor(val labelRes: Int) {
    Low(R.string.color_low_title),
    Charging(R.string.color_charging_title),
    Critical(R.string.color_critical_title),
    HighTemp(R.string.color_high_temp_title),
    ClockWeekday(R.string.color_clock_weekday_title),
    ClockDate(R.string.color_clock_date_title),
    ClockLunar(R.string.color_clock_lunar_title),
    ClockWeather(R.string.color_clock_weather_title),
}

/**
 * The stored colour for one role on one background.
 */
fun colorFor(settings: ModuleSettings, role: RoleColor, onDark: Boolean): Int =
    when (role) {
        RoleColor.Critical -> if (onDark) settings.criticalOnDark else settings.criticalOnLight
        RoleColor.Low -> if (onDark) settings.lowOnDark else settings.lowOnLight
        RoleColor.Charging -> if (onDark) settings.chargingOnDark else settings.chargingOnLight
        RoleColor.ClockWeekday -> if (onDark) settings.clockWeekdayColorOnDark else settings.clockWeekdayColorOnLight
        RoleColor.ClockDate -> if (onDark) settings.clockDateColorOnDark else settings.clockDateColorOnLight
        RoleColor.ClockLunar -> if (onDark) settings.clockLunarColorOnDark else settings.clockLunarColorOnLight
        RoleColor.HighTemp -> if (onDark) settings.highTempOnDark else settings.highTempOnLight
        RoleColor.ClockWeather -> if (onDark) settings.clockWeatherColorOnDark else settings.clockWeatherColorOnLight
    }

fun defaultColorFor(role: RoleColor, onDark: Boolean): Int =
    when (role) {
        RoleColor.Critical -> if (onDark) Prefs.DEF_COLOR_CRITICAL_ON_DARK else Prefs.DEF_COLOR_CRITICAL_ON_LIGHT
        RoleColor.Low -> if (onDark) Prefs.DEF_COLOR_LOW_ON_DARK else Prefs.DEF_COLOR_LOW_ON_LIGHT
        RoleColor.Charging -> if (onDark) Prefs.DEF_COLOR_CHARGING_ON_DARK else Prefs.DEF_COLOR_CHARGING_ON_LIGHT
        RoleColor.HighTemp -> if (onDark) Prefs.DEF_COLOR_HIGH_TEMP_ON_DARK else Prefs.DEF_COLOR_HIGH_TEMP_ON_LIGHT
        RoleColor.ClockWeekday, RoleColor.ClockDate, RoleColor.ClockLunar, RoleColor.ClockWeather -> Prefs.DEF_COLOR_CLOCK
    }

/**
 * Dialog for picking custom light/dark colors for a given role.
 */
@Composable
fun ColorDialog(
    role: RoleColor,
    settings: ModuleSettings,
    onPick: (onDark: Boolean, argb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var onDark by remember { mutableStateOf(false) }

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
                    onClick = { onPick(onDark, defaultColorFor(role, onDark)) },
                )
            }
        }
    }
}

/**
 * Confirmation dialog for resetting role colours to system defaults.
 */
@Composable
fun ResetColorsDialog(
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
