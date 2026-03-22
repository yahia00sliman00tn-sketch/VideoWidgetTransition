package com.videowidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class WidgetAnimService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentFrame = 0
    private var isAnimating = false
    private var frameCount = 0
    private var isLocked = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isLocked = true
                    stopAnimation()
                    showFirstFrame()
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (isLocked) {
                        isLocked = false
                        startAnimation()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
        frameCount = VideoWidget.getFrameCount(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        showFirstFrame()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        frameCount = VideoWidget.getFrameCount(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        stopAnimation()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForeground() {
        val channelId = "widget_anim"
        val channel = NotificationChannel(
            channelId, "Widget Animation",
            NotificationManager.IMPORTANCE_MIN
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Video Widget")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }

    private fun showFirstFrame() {
        val bitmap = VideoWidget.getFrame(this, 0)
        VideoWidget.updateWidget(this, bitmap)
    }

    private fun startAnimation() {
        if (isAnimating || frameCount == 0) return
        isAnimating = true
        currentFrame = 0
        animateNextFrame()
    }

    private fun animateNextFrame() {
        if (!isAnimating || currentFrame >= frameCount) {
            isAnimating = false
            // ثبّت على الفريم الأخير
            val lastFrame = VideoWidget.getFrame(this, frameCount - 1)
            VideoWidget.updateWidget(this, lastFrame)
            return
        }

        val bitmap = VideoWidget.getFrame(this, currentFrame)
        if (bitmap != null) {
            VideoWidget.updateWidget(this, bitmap)
        }

        currentFrame++
        // 33ms = ~30fps
        handler.postDelayed({ animateNextFrame() }, 33)
    }

    private fun stopAnimation() {
        isAnimating = false
        handler.removeCallbacksAndMessages(null)
    }
}
