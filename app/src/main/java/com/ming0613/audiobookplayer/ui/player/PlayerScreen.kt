package com.ming0613.audiobookplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 播放页占位 —— Epic B（播放功能）将在下一个 PR 中实现。
 * 当前只接收并展示 bookId，验证导航链路通畅。
 */
@Composable
fun PlayerScreen(bookId: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("播放页", style = MaterialTheme.typography.headlineSmall)
        Text(
            "播放器开发中（Sprint 1 · Epic B）",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
