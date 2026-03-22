package com.videowidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.io.File

class VideoWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_FRAME = "com.videowidget.UPDATE_FRAME"
        const val EXTRA_FRAME_INDEX = "frame_index"

        fun updateWidget(context: Context, bitmap: Bitmap? = null) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, VideoWidget::class.java)
            )
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widgetImage, bitmap)
            } else {
                // اعرض الفريم الأول المحفوظ
                val firstFrame = getFrame(context, 0)
                if (firstFrame != null) {
                    views.setImageViewBitmap(R.id.widgetImage, firstFrame)
                }
            }

            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        fun getFrame(context: Context, index: Int): Bitmap? {
            val file = File(context.filesDir, "frames/frame_$index.jpg")
            return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }

        fun getFrameCount(context: Context): Int {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("frame_count", 0)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidget(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // شغّل الـ Service عند إضافة الويدجت
        val intent = Intent(context, WidgetAnimService::class.java)
        context.startService(intent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // أوقف الـ Service عند إزالة الويدجت
        context.stopService(Intent(context, WidgetAnimService::class.java))
    }
}
