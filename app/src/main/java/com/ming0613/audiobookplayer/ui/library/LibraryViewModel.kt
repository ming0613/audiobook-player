package com.ming0613.audiobookplayer.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ming0613.audiobookplayer.data.LibraryRepository
import com.ming0613.audiobookplayer.data.SettingsRepository
import com.ming0613.audiobookplayer.data.db.AudiobookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 书架页展示用的一本书（已算好百分比，UI 直接渲染） */
data class BookUi(
    val id: String,
    val name: String,
    val chapterCount: Int,
    val progressPercent: Int,
    val lastPlayedAt: Long
)

/** 书架页的完整状态：UI 只需要观察这一个对象 */
data class LibraryUiState(
    /** null=正在读取配置；true/false=是否已设置书库 */
    val rootConfigured: Boolean? = null,
    val scanning: Boolean = false,
    val books: List<BookUi> = emptyList(),
    val message: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val repository = LibraryRepository(application)
    private val dao = AudiobookDatabase.get(application).bookDao()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** 数据库 -> UI 模型的转换流：数据库一变，书架自动刷新 */
    val books: StateFlow<List<BookUi>> = dao.observeBooks()
        .map { list ->
            list.map { book ->
                val listenedMs =
                    dao.listenedMsBefore(book.id, book.currentChapterIndex) + book.currentPositionMs
                val percent = if (book.totalDurationMs > 0) {
                    (listenedMs * 100 / book.totalDurationMs).toInt().coerceIn(0, 100)
                } else 0
                BookUi(
                    id = book.id,
                    name = book.name,
                    chapterCount = book.chapterCount,
                    progressPercent = percent,
                    lastPlayedAt = book.lastPlayedAt
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 启动时读取已保存的书库目录；有则自动扫描
        viewModelScope.launch {
            val root = settings.rootTreeUri.first()
            _uiState.value = _uiState.value.copy(rootConfigured = root != null)
            if (root != null) scan(root)
        }
    }

    /** 用户在系统文件夹选择器里选了书库根目录（US-A1） */
    fun onRootFolderSelected(uri: Uri) {
        val context = getApplication<Application>()
        // 持久化授权：重启手机后仍有权读取该目录
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        viewModelScope.launch {
            settings.saveRootTreeUri(uri.toString())
            _uiState.value = _uiState.value.copy(rootConfigured = true)
            scan(uri.toString())
        }
    }

    /** 重新扫描当前书库（顶栏刷新按钮） */
    fun rescan() {
        viewModelScope.launch {
            val root = settings.rootTreeUri.first() ?: return@launch
            scan(root)
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun scan(treeUri: String) {
        _uiState.value = _uiState.value.copy(scanning = true)
        val result = repository.scanLibrary(treeUri)
        _uiState.value = _uiState.value.copy(
            scanning = false,
            message = "扫描完成：发现 ${result.bookCount} 本书，共 ${result.chapterCount} 章"
        )
    }
}
