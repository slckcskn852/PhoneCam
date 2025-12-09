package com.phonecam

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Main Activity for RTSP streaming version
 * Two-page layout:
 * 1. Connection page (portrait/landscape) - IP entry, bitrate slider, connect button
 * 2. Preview page (landscape only) - camera preview with disconnect button
 */
class RtspMainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var client: RtspStreamer

    // Connection page views
    private lateinit var connectionPage: View
    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button
    private lateinit var bitrateSlider: SeekBar
    private lateinit var bitrateLabel: TextView

    // Preview page views
    private lateinit var previewPage: View
    private lateinit var zoomLabel: TextView
    private lateinit var streamStatusText: TextView
    private lateinit var disconnectBtn: Button

    private var isConnected = false
    private var currentBitrateMbps = 15

    // Zoom support
    private var currentZoom = 1.0f
    private var maxZoom = 1.0f
    private var minZoom = 1.0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            // Permissions granted, ready to connect
            statusText.text = "Ready to connect"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rtsp)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize connection page views
        connectionPage = findViewById(R.id.connectionPage)
        urlInput = findViewById(R.id.serverUrl)
        statusText = findViewById(R.id.statusText)
        connectBtn = findViewById(R.id.connectBtn)
        bitrateSlider = findViewById(R.id.bitrateSlider)
        bitrateLabel = findViewById(R.id.bitrateLabel)

        // Initialize preview page views
        previewPage = findViewById(R.id.previewPage)
        zoomLabel = findViewById(R.id.zoomLabel)
        streamStatusText = findViewById(R.id.streamStatusText)
        disconnectBtn = findViewById(R.id.disconnectBtn)

        // Initialize RTSP streamer
        client = RtspStreamer(this, currentBitrateMbps) { status ->
            runOnUiThread {
                if (isConnected) {
                    streamStatusText.text = status
                } else {
                    statusText.text = status
                }
                
                if (status.contains("Streaming", ignoreCase = true)) {
                    if (!isConnected) {
                        isConnected = true
                        showPreviewPage()
                    }
                } else if (status.contains("Disconnected", ignoreCase = true) || 
                           status.contains("Error", ignoreCase = true) ||
                           status.contains("failed", ignoreCase = true)) {
                    if (isConnected) {
                        isConnected = false
                        showConnectionPage()
                    }
                    statusText.text = status
                }
            }
        }
        
        // Setup zoom state callback from streamer
        client.onZoomStateChanged = { min, max, current ->
            runOnUiThread {
                minZoom = min
                maxZoom = max
                currentZoom = current
                zoomLabel.text = "Zoom: ${String.format("%.1f", current)}x"
            }
        }

        // Setup bitrate slider (0-25 maps to 5-30 Mbps)
        bitrateSlider.progress = currentBitrateMbps - 5
        bitrateLabel.text = "$currentBitrateMbps Mbps"
        bitrateSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBitrateMbps = progress + 5
                bitrateLabel.text = "$currentBitrateMbps Mbps"
                client.setBitrate(currentBitrateMbps)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup pinch-to-zoom for preview page
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isConnected) {
                    val scaleFactor = detector.scaleFactor
                    val newZoom = (currentZoom * scaleFactor).coerceIn(minZoom, maxZoom)
                    setZoom(newZoom)
                }
                return true
            }
        })

        // Touch listener for preview page (pinch-to-zoom)
        previewPage.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        // Connect button
        connectBtn.setOnClickListener {
            if (!hasPermissions()) {
                statusText.text = "Camera permission required"
                ensurePermissions()
                return@setOnClickListener
            }
            
            val url = urlInput.text.toString().ifBlank {
                getString(R.string.default_rtsp_url)
            }
            
            statusText.text = "Connecting..."
            client.connect(url, this)
        }

        // Disconnect button
        disconnectBtn.setOnClickListener {
            client.disconnect()
            isConnected = false
            showConnectionPage()
            statusText.text = "Disconnected"
        }

        // Request permissions on start
        if (!hasPermissions()) {
            ensurePermissions()
        }
    }

    private fun showConnectionPage() {
        // Allow any orientation on connection page
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        
        connectionPage.visibility = View.VISIBLE
        previewPage.visibility = View.GONE
        
        // Show system bars on connection page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun showPreviewPage() {
        // Force landscape on preview page (allow sensor-based landscape switching)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        connectionPage.visibility = View.GONE
        previewPage.visibility = View.VISIBLE
        
        // Fullscreen immersive on preview page
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun setZoom(zoom: Float) {
        currentZoom = zoom
        client.setZoom(zoom)
        zoomLabel.text = "Zoom: ${String.format("%.1f", zoom)}x"
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == 
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != 
            PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.CAMERA
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isConnected) {
            // Disconnect instead of exiting when on preview page
            client.disconnect()
            isConnected = false
            showConnectionPage()
            statusText.text = "Disconnected"
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        client.stop()
        scope.cancel()
    }
}
