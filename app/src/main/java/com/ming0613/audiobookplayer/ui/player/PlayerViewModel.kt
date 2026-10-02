package com.ming0613.audiobookplayer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ming0613.audiobookplayer.data.SettingsRepository
import com.ming0613.audiobookplayer.data.db.AudiobookDatabase
import com.ming0613.audiobookplayer.data.db.ChapterEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 倍速档位（US-B3） */
val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

data class PlayerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val bookName: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chapterName: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    /** 用户正在拖动进度条时为 true，此时暂停自动刷新避免抖动 */
    val dragging: Boolean = false
)

/**
 * 播放页的大脑。直接持有 ExoPlayer 实例（简单 App 的实用做法；
 * 后续做后台播放时会迁移到 MediaSessionService）。
 *
 * 进度保存策略（US-C1）：
 *  - 播放中每 5 秒自动保存
 *  - 暂停、切章、退出页面时立即保存
 */
class PlayerViewModel(
    application: Application,
    private val bookId: String
) : AndroidViewModel(application), Player.Listener {

    private val dao = AudiobookDatabase.get(application).bookDao()
    private val settings = SettingsRepository(application)

    private val player: ExoPlayer = ExoPlayer.Builder(application).build()
    private var chapters: List<ChapterEntity> = emptyList()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var positionJob: Job? = null
    private var saveJob: Job? = null

    init {
        player.addListener(this)
        viewModelScope.launch {
            val book = dao.getBook(bookId)
            chapters = dao.getChapters(bookId)
            if (book == null || chapters.isEmpty()) {
                _uiState.value = PlayerUiState(loading = false, error = "书籍数据不存在")
                return@launch
            }

            // 恢复上次倍速 + 从上次位置续听（US-B1）
            val speed = settings.playbackSpeed.first()
            val startIndex = book.currentChapterIndex.coerceIn(0, chapters.size - 1)
            player.setPlaybackSpeed(speed)
            player.setMediaItems(
                chapters.map { MediaItem.fromUri(it.uri) },
                startIndex,
                book.currentPositionMs.coerceAtLeast(0)
            )
            player.prepare()

            _uiState.value = PlayerUiState(
                loading = false,
                bookName = book.name,
                chapterIndex = startIndex,
                chapterCount = chapters.size,
                chapterName = chapters[startIndex].name,
                speed = speed
            )
            startPositionPolling()
            startPeriodicSave()
        }
    }

    // ---- 用户操作 ----

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs, dragging = false)
        saveProgress()
    }

    fun onDragStart() {
        _uiState.value = _uiState.value.copy(dragging = true)
    }

    fun onDrag(positionMs: Long) {
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun nextChapter() = player.seekToNextMediaItem()

    fun prevChapter() = player.seekToPreviousMediaItem()

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(speed = speed)
        viewModelScope.launch { settings.savePlaybackSpeed(speed) }
    }

    // ---- ExoPlayer 事件回调 ----

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        if (!isPlaying) saveProgress()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        saveProgress() // 先保存上一章的结尾位置
        updateChapterInfo()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            _uiState.value = _uiState.value.copy(
                durationMs = player.duration.coerceAtLeast(0)
            )
        }
    }

    // ---- 内部机制 ----

    private fun updateChapterInfo() {
        val index = player.currentMediaItemIndex.coerceIn(0, chapters.size - 1)
        _uiState.value = _uiState.value.copy(
            chapterIndex = index,
            chapterName = chapters.getOrNull(index)?.name.orEmpty(),
            positionMs = 0,
            durationMs = player.duration.coerceAtLeast(0)
        )
    }

    /** 每 500ms 同步一次播放位置（驱动进度条），拖动时暂停同步 */
    private fun startPositionPolling() {
        positionJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (!_uiState.value.dragging && player.isPlaying) {
                    _uiState.value = _uiState.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        durationMs = player.duration.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    /** 播放中每 5 秒落盘一次进度（US-C1 的"定期自动保存"） */
    private fun startPeriodicSave() {
        saveJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val chapterIndex = player.currentMediaItemIndex
        val positionMs = player.currentPosition.coerceAtLeast(0)
        CoroutineScope(Dispatchers.IO).launch {
            dao.updateProgress(
                bookId = bookId,
                chapterIndex = chapterIndex,
                positionMs = positionMs,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
    }

    override fun onCleared() {
        saveProgress() // 退出页面前最后一存
        positionJob?.cancel()
        saveJob?.cancel()
        player.release()
        super.onCleared()
    }

    companion object {
        /** 带 bookId 参数的 ViewModel 工厂 */
        fun factory(bookId: String) = viewModelFactory {
            initializer {
                PlayerViewModel(this[APPLICATION_KEY] as Application, bookId)
            }
        }
    }
}
