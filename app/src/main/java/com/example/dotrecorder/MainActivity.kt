package com.example.dotrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Entry screen. Walks the user through the three grants Android requires before
 * a screen recording can start:
 *   1. Overlay permission (to draw the floating dot).
 *   2. Notification permission (Android 13+) for the ongoing-recording notice.
 *   3. The system MediaProjection consent dialog — shown by the OS, not by us.
 *
 * None of these can be skipped or hidden: Android enforces the consent dialog
 * and the status-bar recording indicator for every capture session.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView

    // Launches the system "Start recording or casting?" dialog.
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startRecordingService(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, R.string.consent_declined, Toast.LENGTH_SHORT).show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)

        findViewById<View>(R.id.startButton).setOnClickListener { onStartClicked() }
        findViewById<View>(R.id.stopButton).setOnClickListener {
            ScreenRecordService.stop(this)
            statusText.setText(R.string.status_idle)
        }
    }

    private fun onStartClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        // This call triggers the OS consent dialog. We never see the screen
        // contents until the user approves it here.
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startRecordingService(resultCode: Int, data: Intent) {
        ScreenRecordService.start(this, resultCode, data)
        statusText.setText(R.string.status_recording)
        // Return the user to their home screen so the dot is what they interact with.
        moveTaskToBack(true)
    }
}
