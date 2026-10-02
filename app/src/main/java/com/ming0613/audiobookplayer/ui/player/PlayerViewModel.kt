package com.ming0613.audiobookplayer.ui.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ming0613.audiobookplayer.data.SettingsRepository
import com.ming0613.audiobookplayer.data.db.AudiobookDatabase
import com.ming0613.audiobookplayer.data.db.ChapterEntity
import com.ming0613.audiobookplayer.playback.PlaybackService
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
 * 播放页的大脑（Sprint 2 版）。
 *
 * 与 Sprint 1 的关键区别：不再持有 ExoPlayer，而是持有 MediaController——
 * 一个指向 PlaybackService 里播放器的"遥控器"。
 * 本类只负责：发命令、收状态、驱动 UI。
 * 进度保存已上移到 PlaybackService（UI 死了也要继续落盘）。
 */
class PlayerViewModel(
    application: Application,
    private val bookId: String
) : AndroidViewModel(application), Player.Listener {

    private val dao = AudiobookDatabase.get(application).bookDao()
    private val settings = SettingsRepository(application)

    /** 遥控器：异步连接，连接成功前为 null，所有命令都要判空 */
    private var controller: MediaController? = null
    private var chapters: List<ChapterEntity> = emptyList()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var positionJob: Job? = null

    init {
        // 连接 PlaybackService：若服务未运行，系统会自动唤起它
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val c = controllerFuture.get()
                controller = c
                c.addListener(this@PlayerViewModel)
                loadBook(c)
            },
            ContextCompat.getMainExecutor(application)
        )
    }

    private fun loadBook(controller: MediaController) {
        viewModelScope.launch {
            val book = dao.getBook(bookId)
            chapters = dao.getChapters(bookId)
            if (book == null || chapters.isEmpty()) {
                _uiState.value = PlayerUiState(loading = false, error = "书籍数据不存在")
                return@launch
            }

            val speed = settings.playbackSpeed.first()
            val startIndex = book.currentChapterIndex.coerceIn(0, chapters.size - 1)

            // bookId 藏进 mediaId：PlaybackService 保存进度时要靠它找到书
            controller.setMediaItems(
                chapters.map {
                    MediaItem.Builder().setMediaId(bookId).setUri(it.uri).build()
                },
                startIndex,
                book.currentPositionMs.coerceAtLeast(0)
            )
            controller.setPlaybackSpeed(speed)
            controller.prepare()

            _uiState.value = PlayerUiState(
                loading = false,
                bookName = book.name,
                chapterIndex = startIndex,
                chapterCount = chapters.size,
                chapterName = chapters[startIndex].name,
                speed = speed
            )
            startPositionPolling()
        }
    }

    // ---- 用户操作（全部转发给遥控器） ----

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs, dragging = false)
    }

    fun onDragStart() {
        _uiState.value = _uiState.value.copy(dragging = true)
    }

    fun onDrag(positionMs: Long) {
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun nextChapter() {
        controller?.seekToNextMediaItem()
    }

    fun prevChapter() {
        controller?.seekToPreviousMediaItem()
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(speed = speed)
        viewModelScope.launch { settings.savePlaybackSpeed(speed) }
    }

    // ---- 播放器事件回调（经遥控器转发自 Service） ----

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateChapterInfo()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            _uiState.value = _uiState.value.copy(
                durationMs = controller?.duration?.coerceAtLeast(0) ?: 0
            )
        }
    }

    // ---- 内部机制 ----

    private fun updateChapterInfo() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex.coerceIn(0, chapters.size - 1)
        _uiState.value = _uiState.value.copy(
            chapterIndex = index,
            chapterName = chapters.getOrNull(index)?.name.orEmpty(),
            positionMs = 0,
            durationMs = c.duration.coerceAtLeast(0)
        )
    }

    /** 每 500ms 同步一次播放位置（驱动进度条），拖动时暂停同步 */
    private fun startPositionPolling() {
        positionJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val c = controller
                if (c != null && !_uiState.value.dragging && c.isPlaying) {
                    _uiState.value = _uiState.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    override fun onCleared() {
        // 只释放"遥控器"，不停止播放——后台播放的关键就在这里
        controller?.removeListener(this)
        controller?.release()
        controller = null
        positionJob?.cancel()
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
