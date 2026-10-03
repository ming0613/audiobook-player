package com.ming0613.audiobookplayer.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ming0613.audiobookplayer.MainActivity
import com.ming0613.audiobookplayer.R
import com.ming0613.audiobookplayer.data.db.AudiobookDatabase
import com.ming0613.audiobookplayer.data.db.BookDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 后台播放服务（US-D1）。
 *
 * 架构核心：ExoPlayer 住在 Service 里，UI（PlayerViewModel）通过
 * MediaController 远程遥控。UI 进程被回收后，只要 Service 存活，
 * 播放和进度保存就不会中断。
 *
 * MediaSessionService 自带媒体通知（播放/暂停/切章按钮），无需手写通知。
 *
 * 进度保存职责（US-C1）已从 ViewModel 上移到这里：
 *  - 播放中每 5 秒自动落盘
 *  - 暂停 / 切章时立即落盘
 *  - 服务销毁时落盘
 * bookId 由 UI 侧写入 MediaItem.mediaId 传过来。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var dao: BookDao

    /** 服务级协程域：独立于任何 UI 生命周期 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        dao = AudiobookDatabase.get(this).bookDao()

        // Media3 默认用 IMPORTANCE_LOW 频道发媒体通知，
        // 在部分 ROM（如 ColorOS）上会被折叠/锁屏不显示。
        // 这里自建 IMPORTANCE_DEFAULT 频道并让框架使用它。
        ensureMediaNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MEDIA_CHANNEL_ID)
                .setChannelName(R.string.media_notification_channel_name)
                .build()
        )

        val player = ExoPlayer.Builder(this).build()

        // 点击通知回到 App
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        // 暂停 / 切章时立即保存进度
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveProgress(player)
            }
        })

        // 播放中每 5 秒落盘一次
        // 注意：ExoPlayer 的所有方法必须在主线程访问（否则抛
        // IllegalStateException: Player is accessed on the wrong thread），
        // 所以轮询循环跑在 Dispatchers.Main，只有数据库写入才切到 IO。
        serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(5000)
                if (player.isPlaying) saveProgress(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** 预先创建"默认重要性"的媒体通知频道；框架检测到已存在就不会再用 LOW 覆盖 */
    private fun ensureMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(MEDIA_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        MEDIA_CHANNEL_ID,
                        getString(R.string.media_notification_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = getString(R.string.media_notification_channel_description)
                    }
                )
            }
        }
    }

    companion object {
        private const val MEDIA_CHANNEL_ID = "media_playback_channel"
    }

    private fun saveProgress(player: Player) {
        val bookId = player.currentMediaItem?.mediaId.orEmpty()
        if (bookId.isEmpty()) return
        val chapterIndex = player.currentMediaItemIndex
        val positionMs = player.currentPosition.coerceAtLeast(0)
        serviceScope.launch {
            dao.updateProgress(
                bookId = bookId,
                chapterIndex = chapterIndex,
                positionMs = positionMs,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            saveProgress(session.player) // 服务销毁前最后一存
            session.player.release()
            session.release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
