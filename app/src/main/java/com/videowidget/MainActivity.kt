package com.videowidget

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnSelectVideo: Button
    private lateinit var btnReset: Button
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val REQUEST_VIDEO = 1001
        const val MAX_FRAMES = 40
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnReset = findViewById(R.id.btnReset)
        progressBar = findViewById(R.id.progressBar)

        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val frameCount = prefs.getInt("frame_count", 0)
        if (frameCount > 0) {
            tvStatus.text = getString(R.string.ready)
        }

        btnSelectVideo.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }
            startActivityForResult(intent, REQUEST_VIDEO)
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.reset))
                .setMessage("هل تريد حذف الفريمات وإعادة التعيين؟")
                .setPositiveButton("نعم") { _, _ ->
                    resetFrames()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            extractFrames(uri)
        }
    }

    private fun extractFrames(uri: Uri) {
        tvStatus.text = getString(R.string.processing)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        btnSelectVideo.isEnabled = false

        thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)

                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLong() ?: 1000L

                // احسب عدد الفريمات
                val frameCount = minOf(MAX_FRAMES, (durationMs / 33).toInt().coerceAtLeast(10))
                val interval = durationMs * 1000L / frameCount // microseconds

                // أنشئ مجلد الفريمات
                val framesDir = File(filesDir, "frames")
                framesDir.mkdirs()

                // احذف الفريمات القديمة
                framesDir.listFiles()?.forEach { it.delete() }

                for (i in 0 until frameCount) {
                    val timeUs = i * interval
                    val bitmap = retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )

                    if (bitmap != null) {
                        // اضغط وحفظ
                        val file = File(framesDir, "frame_$i.jpg")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        bitmap.recycle()
                    }

                    val progress = ((i + 1) * 100 / frameCount)
                    handler.post {
                        progressBar.progress = progress
                        tvStatus.text = "${getString(R.string.processing)} $progress%"
                    }
                }

                retriever.release()

                // احفظ عدد الفريمات
                val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
                prefs.edit().putInt("frame_count", frameCount).apply()

                // حدّث الويدجت
                VideoWidget.updateWidget(this)

                // شغّل الـ Service
                startForegroundService(Intent(this, WidgetAnimService::class.java))

                handler.post {
                    progressBar.visibility = View.GONE
                    tvStatus.text = getString(R.string.ready)
                    btnSelectVideo.isEnabled = true
                }

            } catch (e: Exception) {
                handler.post {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "خطأ: ${e.message}"
                    btnSelectVideo.isEnabled = true
                }
            }
        }
    }

    private fun resetFrames() {
        val framesDir = File(filesDir, "frames")
        framesDir.listFiles()?.forEach { it.delete() }
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        prefs.edit().putInt("frame_count", 0).apply()
        tvStatus.text = getString(R.string.no_video)
        VideoWidget.updateWidget(this, null)
    }
}
