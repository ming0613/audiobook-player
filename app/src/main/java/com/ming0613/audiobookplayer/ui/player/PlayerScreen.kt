package com.ming0613.audiobookplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 播放页（US-B1 续听 / US-B2 播放控制 / US-B3 倍速）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(bookId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Android 13+ 需要动态申请通知权限，否则播放通知不显示
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 结果不强制处理：拒绝后只是看不到通知，播放不受影响 */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.bookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回书架")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                uiState.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                uiState.error != null -> Text(
                    text = uiState.error ?: "",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> PlayerContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PlayerContent(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 章节信息
        Text(
            text = "第 ${uiState.chapterIndex + 1} 章 / 共 ${uiState.chapterCount} 章",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.chapterName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 进度条 + 时间
        Slider(
            value = uiState.positionMs.toFloat().coerceIn(0f, uiState.durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = {
                if (!uiState.dragging) viewModel.onDragStart()
                viewModel.onDrag(it.toLong())
            },
            onValueChangeFinished = { viewModel.seekTo(uiState.positionMs) },
            valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatMs(uiState.positionMs), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text(formatMs(uiState.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 播放控制：上一章 / 播放暂停 / 下一章（US-B2）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IconButton(onClick = { viewModel.prevChapter() }, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一章",
                    modifier = Modifier.size(40.dp)
                )
            }
            FilledIconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors()
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = { viewModel.nextChapter() }, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一章",
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 倍速选择（US-B3）
        SpeedSelector(
            currentSpeed = uiState.speed,
            onSpeedSelected = { viewModel.setSpeed(it) }
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SpeedSelector(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("倍速 ${formatSpeed(currentSpeed)}", style = MaterialTheme.typography.titleSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SPEED_OPTIONS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSpeed(speed),
                            color = if (speed == currentSpeed)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}.0x" else "${speed}x"

/** 毫秒 -> 时长文本；超过 1 小时显示 h:mm:ss，否则 m:ss */
fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
