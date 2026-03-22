package com.videowidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.view.TextureView
import android.view.WindowManager
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var textureView: TextureView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isLocked = true
    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isLocked = true
                    hideOverlay()
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (isLocked) {
                        isLocked = false
                        showOverlay()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        hideOverlay()
        releasePlayer()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "overlay_service"
        val channel = NotificationChannel(
            channelId, "Video Overlay",
            NotificationManager.IMPORTANCE_MIN
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Video Overlay")
            .setContentText("جاهز...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }

    private fun showOverlay() {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val uriString = prefs.getString("video_uri", null) ?: return

        handler.post {
            try {
                // أنشئ TextureView
                val tv = TextureView(this)
                textureView = tv

                // إعدادات النافذة — كاملة الشاشة شفافة
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )

                windowManager?.addView(tv, params)

                // انتظر حتى يكون السطح جاهزاً
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture, w: Int, h: Int
                    ) {
                        playVideo(Uri.parse(uriString), Surface(surface))
                    }
                    override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playVideo(uri: Uri, surface: Surface) {
        try {
            val player = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(applicationContext, uri)
                isLooping = false
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener {
                    // الفيديو انتهى — أخفِ الـ Overlay
                    handler.postDelayed({ hideOverlay() }, 50)
                }
                setOnErrorListener { _, _, _ ->
                    handler.post { hideOverlay() }
                    false
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            hideOverlay()
        }
    }

    private fun hideOverlay() {
        handler.post {
            try {
                releasePlayer()
                textureView?.let {
                    windowManager?.removeView(it)
                }
                textureView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
    }
}
