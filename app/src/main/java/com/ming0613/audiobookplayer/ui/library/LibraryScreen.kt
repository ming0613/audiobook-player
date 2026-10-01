package com.ming0613.audiobookplayer.ui.library

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Date

/**
 * 书架页（US-A1 / US-A2 / US-A3 / US-C2）
 * 三种状态：未设置书库 -> 引导页；扫描中 -> 加载态；正常 -> 书籍列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 文件夹选择器：系统弹窗，用户授权一个目录给我们
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onRootFolderSelected(it) }
    }

    // 扫描完成等一次性消息 -> Snackbar 提示
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的书架") },
                actions = {
                    if (uiState.rootConfigured == true) {
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.Settings, contentDescription = "更换书库")
                        }
                        IconButton(onClick = { viewModel.rescan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                uiState.rootConfigured == null -> LoadingContent()
                uiState.rootConfigured == false -> WelcomeContent(
                    onPickFolder = { folderPicker.launch(null) }
                )
                books.isEmpty() && !uiState.scanning -> EmptyContent(
                    onPickFolder = { folderPicker.launch(null) }
                )
                else -> BookList(books = books, onBookClick = onBookClick)
            }
            if (uiState.scanning) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun WelcomeContent(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("欢迎来到有声书播放器", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "选择存放有声书的文件夹，\n每个子文件夹会被识别为一本书。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onPickFolder) { Text("选择书库文件夹") }
    }
}

@Composable
private fun EmptyContent(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("书库是空的", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("所选文件夹下没有发现包含音频的子文件夹")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onPickFolder) { Text("重新选择文件夹") }
    }
}

@Composable
private fun BookList(books: List<BookUi>, onBookClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        items(books, key = { it.id }) { book ->
            BookItem(book = book, onClick = { onBookClick(book.id) })
        }
    }
}

@Composable
private fun BookItem(book: BookUi, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${book.chapterCount} 章",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (book.lastPlayedAt > 0)
                        "上次收听 " + DateFormat.format("MM-dd HH:mm", Date(book.lastPlayedAt))
                    else
                        "还未开始",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${book.progressPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { book.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
