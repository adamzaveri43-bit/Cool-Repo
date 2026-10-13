package com.example.dotrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that holds the MediaProjection session, writes the MP4,
 * and shows the floating dot control. The service is mandatory: Android only
 * lets a MediaProjection run while a foreground service of type
 * "mediaProjection" is alive, and that service keeps the recording indicator
 * visible in the status bar for the whole session.
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var floatingControl: FloatingControl? = null
    private var outputFile: File? = null
    private var recording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data != null) beginRecording(resultCode, data)
            }
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val channelId = "screen_record"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun beginRecording(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)?.also {
            // Android 14+ requires a registered callback.
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopEverything()
            }, null)
        }
        if (projection == null) {
            Toast.makeText(this, R.string.projection_failed, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        recorder = buildRecorder(width, height).apply { prepare() }

        virtualDisplay = projection!!.createVirtualDisplay(
            "DotRecorder",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder!!.surface, null, null
        )

        recorder!!.start()
        recording = true

        floatingControl = FloatingControl(
            this,
            onToggleStop = { stopEverything() }
        ).also { it.show() }
    }

    private fun buildRecorder(width: Int, height: Int): MediaRecorder {
        val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dir, "rec_$stamp.mp4")

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return r.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(8_000_000)
            setOutputFile(outputFile!!.absolutePath)
        }
    }

    private fun stopEverything() {
        if (!recording) {
            stopSelf()
            return
        }
        recording = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // stop() throws if no frames were written yet; ignore.
        }
        recorder?.reset()
        recorder?.release()
        recorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        projection?.stop()
        projection = null

        floatingControl?.hide()
        floatingControl = null

        outputFile?.let {
            Toast.makeText(this, getString(R.string.saved, it.name), Toast.LENGTH_LONG).show()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingControl?.hide()
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_RESULT_CODE = "code"
        private const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
