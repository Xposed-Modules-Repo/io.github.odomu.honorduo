/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.odomu.honorduo.ui.screen

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.odomu.honorduo.BuildConfig
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.ui.component.AmbientMeshBackground
import io.github.odomu.honorduo.ui.dialog.ReleaseNotesDialog
import io.github.odomu.honorduo.ui.manager.UpdateController
import io.github.odomu.honorduo.ui.manager.UpdateState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Scrolling brand header over a persistent, full-screen animated mesh. */
@Composable
internal fun AboutPage(
    updater: UpdateController,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    var showReleaseNotes by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val repoUrl = "https://github.com/${BuildConfig.REPO_SLUG}"
    val originalRepoUrl = "https://github.com/yixing233/HyperDuo"
    val hasRepo = BuildConfig.REPO_SLUG.isNotBlank()
    val configuration = LocalConfiguration.current
    val isDark = isSystemInDarkTheme()
    val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val screenHeightDp = configuration.screenHeightDp.dp
    val heroAreaHeight = (screenHeightDp * 0.58f).coerceIn(420.dp, 560.dp)

    val scrollOffset by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            val off = listState.firstVisibleItemScrollOffset
            if (idx > 0) 800f else off.toFloat()
        }
    }

    val scrollProgress by remember {
        derivedStateOf {
            (scrollOffset / 320f).coerceIn(0f, 1f)
        }
    }

    val state = updater.state
    val updateSummary: String = when (state) {
        is UpdateState.Idle -> "检查是否有可用新版本"
        is UpdateState.Checking -> "正在检查更新…"
        is UpdateState.UpToDate -> "已是最新版本 ${state.current}"
        is UpdateState.NoRelease -> "当前为最新发布版本"
        is UpdateState.Available -> "发现新版本 ${state.info.version}，点击查看"
        is UpdateState.Downloading -> "正在下载 ${(state.progress * 100).toInt()}%"
        is UpdateState.Ready -> "新版本已下载完成，点击安装"
        is UpdateState.Failed -> state.detail?.takeIf { it.isNotBlank() } ?: "检查更新失败"
    }

    val shaderSupported = remember { isRuntimeShaderSupported() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = {}),
    ) {
        if (shaderSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AmbientMeshBackground(
                isDark = isDark,
            )
        } else {
            val fallbackBrush = if (isDark) {
                Brush.verticalGradient(listOf(Color(0xFF161F2E), Color(0xFF1E172E), Color(0xFF121824)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFEBF1FA), Color(0xFFF6ECF8), Color(0xFFEAF1F8)))
            }
            Box(modifier = Modifier.fillMaxSize().background(fallbackBrush))
        }

        // Dedicated Top Hero Area with generous top padding from status bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroAreaHeight)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp)
                .graphicsLayer {
                    translationY = -scrollOffset * 0.45f
                    alpha = (1f - (scrollProgress * 1.35f)).coerceIn(0f, 1f)
                    scaleX = 1f - (scrollProgress * 0.08f)
                    scaleY = 1f - (scrollProgress * 0.08f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val glyphColor = MiuixTheme.colorScheme.onSurface
            Canvas(modifier = Modifier.size(92.dp)) {
                val unit = size.minDimension / 120f
                val center = Offset(size.width / 2f, size.height / 2f)

                // 1. Outer battery ring
                val ringR = 46f * unit
                drawArc(
                    color = glyphColor,
                    startAngle = 145f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = center - Offset(ringR, ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = 5.6f * unit, cap = StrokeCap.Round),
                )

                // 2. Four bottom cellular signal dots
                val dotRadius = 3.6f * unit
                val dotAngles = intArrayOf(58, 79, 101, 122)
                for (deg in dotAngles) {
                    val rad = Math.toRadians(deg.toDouble())
                    val x = center.x + (kotlin.math.cos(rad) * ringR).toFloat()
                    val y = center.y + (kotlin.math.sin(rad) * ringR).toFloat()
                    drawCircle(color = glyphColor, radius = dotRadius, center = Offset(x, y))
                }

                // 3. Inner Wi-Fi glyph optically centered against the ring
                val wifiCenter = Offset(center.x, center.y + 10.5f * unit)
                fun wifiArc(r: Float, strokeWidth: Float) {
                    val radius = r * unit
                    drawArc(
                        color = glyphColor,
                        startAngle = 222f,
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = wifiCenter - Offset(radius, radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth * unit, cap = StrokeCap.Round),
                    )
                }

                wifiArc(25f, 5.0f)
                wifiArc(15f, 5.0f)
                drawCircle(color = glyphColor, radius = 3.2f * unit, center = wifiCenter)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.xposed_description),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp),
                lineHeight = 18.sp,
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.about_title),
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    color = Color.Transparent,
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = navPadding + 40.dp,
                ),
            ) {
                // Reserve space for the hero area
                item(key = "heroSpacer") {
                    Spacer(modifier = Modifier.height(heroAreaHeight - 32.dp))
                }

                // Consolidated actions and settings card
                item(key = "aboutActionGroup") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = if (isDark) Color(0xFF24242A) else Color.White,
                        ),
                    ) {
                        AboutAction(
                            title = stringResource(R.string.update_title),
                            summary = updateSummary,
                            busy = state is UpdateState.Checking || state is UpdateState.Downloading,
                            onClick = onCheckUpdate,
                        )
                        AboutAction(
                            title = stringResource(R.string.release_notes_title),
                            summary = stringResource(R.string.release_notes_summary),
                            onClick = { showReleaseNotes = true },
                        )
                        if (hasRepo) {
                            AboutAction(
                                title = stringResource(R.string.view_repository_title),
                                summary = stringResource(R.string.view_repository_summary),
                                onClick = { updater.openInBrowser(repoUrl) },
                            )
                        }
                        AboutAction(
                            title = stringResource(R.string.view_original_repo_title),
                            summary = stringResource(R.string.view_original_repo_summary),
                            onClick = { updater.openInBrowser(originalRepoUrl) },
                        )
                    }
                }

                item(key = "bottomPadding") {
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
            ReleaseNotesDialog(
                show = showReleaseNotes,
                info = (updater.state as? UpdateState.Available)?.info,
                currentVersion = BuildConfig.VERSION_NAME,
                downloadProgress = null,
                dontRemind = false,
                onDontRemindChange = {},
                onDismiss = { showReleaseNotes = false },
                onDownload = { info -> updater.download(info) },
            )
        }
    }
}

@Composable
private fun AboutAction(
    title: String,
    summary: String? = null,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface)
            if (summary != null) {
                Spacer(Modifier.size(2.dp))
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        if (busy) {
            CircularProgressIndicator(size = 18.dp)
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
