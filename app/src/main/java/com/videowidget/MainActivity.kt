package com.videowidget

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnSelectVideo: Button
    private lateinit var btnToggle: Button
    private lateinit var btnReset: Button
    private var isServiceRunning = false

    companion object {
        const val REQUEST_VIDEO = 1001
        const val REQUEST_OVERLAY = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnToggle = findViewById(R.id.btnToggle)
        btnReset = findViewById(R.id.btnReset)

        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val savedUri = prefs.getString("video_uri", null)
        if (savedUri != null) {
            tvStatus.text = getString(R.string.ready)
            btnToggle.isEnabled = true
        }

        updateToggleButton()

        btnSelectVideo.setOnClickListener {
            // تحقق من صلاحية Overlay أولاً
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("صلاحية مطلوبة")
                    .setMessage("نحتاج صلاحية عرض فوق التطبيقات لتشغيل الفيديو عند فتح الهاتف")
                    .setPositiveButton("منح الصلاحية") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, REQUEST_OVERLAY)
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
                return@setOnClickListener
            }
            pickVideo()
        }

        btnToggle.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, OverlayService::class.java))
                isServiceRunning = false
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                isServiceRunning = true
            }
            updateToggleButton()
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.reset))
                .setMessage("هل تريد إعادة التعيين؟")
                .setPositiveButton("نعم") { _, _ ->
                    stopService(Intent(this, OverlayService::class.java))
                    isServiceRunning = false
                    val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    tvStatus.text = getString(R.string.no_video)
                    btnToggle.isEnabled = false
                    updateToggleButton()
                    Toast.makeText(this, "تمت الإعادة ✓", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    private fun updateToggleButton() {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val hasVideo = prefs.getString("video_uri", null) != null
        btnToggle.isEnabled = hasVideo
        btnToggle.text = if (isServiceRunning) "⏹ إيقاف" else "▶ تشغيل"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_VIDEO -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data ?: return
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
                    prefs.edit().putString("video_uri", uri.toString()).apply()
                    tvStatus.text = getString(R.string.ready)
                    btnToggle.isEnabled = true
                    // شغّل الـ Service فوراً
                    startForegroundService(Intent(this, OverlayService::class.java))
                    isServiceRunning = true
                    updateToggleButton()
                    Toast.makeText(this, getString(R.string.video_selected), Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    pickVideo()
                }
            }
        }
    }
}
