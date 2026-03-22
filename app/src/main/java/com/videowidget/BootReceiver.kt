package com.videowidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // أعد تشغيل الـ Service بعد إعادة التشغيل
            val serviceIntent = Intent(context, WidgetAnimService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
