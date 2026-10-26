package io.github.odomu.honorduo.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.component.navigationDragInput
import io.github.odomu.honorduo.ui.component.rememberNavigationDragState
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

enum class SettingsTab(val labelRes: Int, val route: Route) {
    About(R.string.tab_home, Route.About),
    Layout(R.string.tab_layout, Route.Layout),
    Settings(R.string.tab_settings, Route.Settings),
}

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Layout : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route
}

@Composable
fun DockedNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: LayerBackdrop?,
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
                    icon = iconFor(tab),
                    label = if (labels == Prefs.NAV_LABELS_ICON) "" else stringResource(tab.labelRes),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .navigationBarsPadding()
                .navigationDragInput(interaction),
        )
    }
}

@Composable
fun iconFor(tab: SettingsTab): ImageVector = when (tab) {
    SettingsTab.About -> ImageVector.vectorResource(R.drawable.ic_lucide_home)
    SettingsTab.Layout -> ImageVector.vectorResource(R.drawable.ic_lucide_circle_dot)
    SettingsTab.Settings -> ImageVector.vectorResource(R.drawable.ic_lucide_sliders_horizontal)
}

@Composable
fun FloatingNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: LayerBackdrop?,
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

@Composable
fun LiquidNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
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

@Composable
fun NavigationPill(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: LayerBackdrop?,
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

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .navigationDragInput(interaction)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(16.dp.toPx())
                                lens(
                                    lensHeight.toPx() * (1f + 0.15f * press),
                                    lensAmount.toPx() * (1f + 0.20f * press),
                                    chromaticAberration = chromaticAberration,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    width = 0.75.dp,
                                    blurRadius = 0.4.dp,
                                    alpha = 0.76f,
                                )
                            },
                            onDrawSurface = {
                                drawRect(surface)
                                if (liquid) {
                                    val pillWidth = (size.width - 8.dp.toPx()) / count
                                    drawCircle(
                                        color = accent.copy(alpha = 0.28f * press),
                                        radius = pillWidth * 0.55f * (0.8f + 0.2f * press),
                                        center = androidx.compose.ui.geometry.Offset(
                                            size.width / 2f + direction *
                                                (interaction.position - (count - 1) / 2f) * (size.width - 8.dp.toPx()) / count,
                                            size.height / 2f,
                                        ),
                                    )
                                }
                            },
                        )
                    } else {
                        Modifier
                            .clip(shape)
                            .background(surface)
                    },
                )
                .padding(4.dp),
        ) {
            val slot = (maxWidth - 8.dp) / count
            val slotPx = with(density) { slot.toPx() }
            Box(
                Modifier
                    .width(slot)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = direction * (interaction.position - (count - 1) / 2f) * slotPx
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (liquid) accent.copy(alpha = 0.16f + 0.14f * press)
                        else if (darkTheme) Color.White.copy(alpha = 0.14f)
                        else Color.Black.copy(alpha = 0.08f),
                    ),
            )

            NavigationTabRow(
                labels = labels,
                openIndex = openIndex,
                color = ink,
                modifier = Modifier.fillMaxSize(),
                onSelect = onSelect,
            )
        }
    }
}

@Composable
fun NavigationTabRow(
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
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier.graphicsLayer { scaleX = contentScale(); scaleY = contentScale() },
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        modifier = if (showIcon) Modifier.padding(top = 1.6.dp) else Modifier,
                    )
                }
            }
        }
    }
}
