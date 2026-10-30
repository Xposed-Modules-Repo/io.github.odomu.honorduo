package io.github.odomu.honorduo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

val SettingsItemMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp)

/** Height of one settings tile: icon, title, and a two-line summary, plus the padding. */
private val TILE_HEIGHT = 104.dp

internal val ActivatedContainer = Color(0xFFE4F9E5)
internal val ActivatedContent = Color(0xFF101010)
internal val ActivatedBadge = Color(0xFF7FD78A)

internal val ActivatedContainerDark = Color(0xFF1D2A1F)
internal val ActivatedBadgeDark = Color(0xFF6FC77C)

/**
 * Whether the active theme is dark mode.
 */
@Composable
fun isDarkTheme(): Boolean = MiuixTheme.colorSchemeMode == ColorSchemeMode.Dark ||
    (MiuixTheme.colorSchemeMode == ColorSchemeMode.System && isSystemInDarkTheme())

/**
 * Common list container for settings tabs. Resets scroll offset on tab visit.
 */
@Composable
fun SectionList(
    contentPadding: PaddingValues,
    isTop: Boolean,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(isTop) {
        if (isTop) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

/**
 * One entry of the settings grid: the same "opens a sheet" idea as [SettingPreviewCard],
 * laid out for two per row.
 *
 * Vertical, not the row layout the full-width card uses: half a phone's width cannot hold an
 * icon, a title and a summary side by side without the summary becoming two words and an
 * ellipsis. So the icon sits on top, the title under it, and the summary is clamped to two
 * lines - the tile states what the sheet is about and nothing more, which is what keeps a
 * grid readable.
 *
 * A fixed height, because the tiles in a row must end level and every summary is clamped to
 * the same two lines: intrinsic sizing would make the row's depth depend on the longest string
 * in this build, and a fixed box is what lets the grid read as a grid.
 */
@Composable
fun SettingTile(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.height(TILE_HEIGHT), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Translucent circular icon button matching Miuix glass styling.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Selectable background theme button chip.
 */
@Composable
fun BackgroundChip(
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

/**
 * Circular color preview swatch.
 */
@Composable
fun Swatch(color: Int) {
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
 */
@Composable
fun IntSlider(
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
            onValueChange = { raw: Float ->
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
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.size(28.dp),
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
    }

    if (showResetConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.reset_setting_title),
            summary = stringResource(R.string.color_reset_dialog_summary),
            onDismissRequest = { showResetConfirm = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    Text(text = stringResource(R.string.color_reset_confirm))
                }
            }
        }
    }
}
