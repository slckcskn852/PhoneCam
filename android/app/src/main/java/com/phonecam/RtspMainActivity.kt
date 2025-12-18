package com.phonecam

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
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
    private lateinit var prefs: SharedPreferences

    // Connection page views
    private lateinit var connectionPage: View
    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button
    private lateinit var bitrateSlider: SeekBar
    private lateinit var bitrateLabel: TextView
    private lateinit var resolutionGroup: RadioGroup
    private lateinit var fpsGroup: RadioGroup

    // Preview page views
    private lateinit var previewPage: View
    private lateinit var cameraPreview: androidx.camera.view.PreviewView
    private lateinit var zoomLabel: TextView
    private lateinit var streamStatusText: TextView
    private lateinit var disconnectBtn: Button
    private lateinit var blackoutOverlay: View

    // UI handler for delayed blackout
    private val uiHandler = Handler(Looper.getMainLooper())
    private var blackoutRunnable: Runnable? = null
    private var originalBrightness: Float = -1f

    private var isConnected = false
    private var currentBitrateMbps = 15
    private var currentWidth = 1920
    private var currentHeight = 1080
    private var currentFps = 60

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
        
        // Load saved preferences
        prefs = getSharedPreferences("phonecam_prefs", Context.MODE_PRIVATE)
        loadPreferences()

        // Initialize connection page views
        connectionPage = findViewById(R.id.connectionPage)
        urlInput = findViewById(R.id.serverUrl)
        statusText = findViewById(R.id.statusText)
        connectBtn = findViewById(R.id.connectBtn)
        bitrateSlider = findViewById(R.id.bitrateSlider)
        bitrateLabel = findViewById(R.id.bitrateLabel)
        resolutionGroup = findViewById(R.id.resolutionGroup)
        fpsGroup = findViewById(R.id.fpsGroup)

        // Initialize preview page views
        previewPage = findViewById(R.id.previewPage)
        cameraPreview = findViewById(R.id.cameraPreview)
        zoomLabel = findViewById(R.id.zoomLabel)
        streamStatusText = findViewById(R.id.streamStatusText)
        disconnectBtn = findViewById(R.id.disconnectBtn)
        
        // Restore saved values to UI
        urlInput.setText(prefs.getString("server_url", ""))

        // Blackout overlay (AMOLED dim)
        blackoutOverlay = findViewById(R.id.blackoutOverlay)
        blackoutOverlay.setOnClickListener {
            // Hide overlay on user touch and restore brightness
            cancelBlackout()
        }

        // Initialize RTSP streamer with saved settings
        client = RtspStreamer(this, cameraPreview, currentBitrateMbps, currentWidth, currentHeight, currentFps) { status ->
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
                        // Schedule AMOLED blackout after 10s of stable connection
                        scheduleBlackout()
                    }
                } else if (status.contains("Disconnected", ignoreCase = true) || 
                           status.contains("Error", ignoreCase = true) ||
                           status.contains("failed", ignoreCase = true)) {
                    if (isConnected) {
                        isConnected = false
                        showConnectionPage()
                        // Cancel any pending blackout and remove overlay
                        cancelBlackout()
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

        // Setup resolution radio buttons
        when (currentHeight) {
            1080 -> resolutionGroup.check(R.id.res1080p)
            720 -> resolutionGroup.check(R.id.res720p)
            else -> resolutionGroup.check(R.id.res1080p)
        }
        resolutionGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.res1080p -> { currentWidth = 1920; currentHeight = 1080 }
                R.id.res720p -> { currentWidth = 1280; currentHeight = 720 }
            }
            client.setResolution(currentWidth, currentHeight)
        }

        // Setup FPS radio buttons
        when (currentFps) {
            60 -> fpsGroup.check(R.id.fps60)
            30 -> fpsGroup.check(R.id.fps30)
        }
        fpsGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFps = when (checkedId) {
                R.id.fps60 -> 60
                R.id.fps30 -> 30
                else -> 60
            }
            client.setFrameRate(currentFps)
        }

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
            cancelBlackout()
            showConnectionPage()
            statusText.text = "Disconnected"
        }

        // Request permissions on start
        if (!hasPermissions()) {
            ensurePermissions()
        }
    }

    private fun showConnectionPage() {
        // Lock to portrait on connection page
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
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
        // store original brightness value (may be -1f for system default)
        try {
            originalBrightness = window.attributes.screenBrightness
        } catch (e: Exception) {
            originalBrightness = -1f
        }
    }

    private fun scheduleBlackout() {
        // cancel previous
        blackoutRunnable?.let { uiHandler.removeCallbacks(it) }
        blackoutRunnable = Runnable { showBlackoutOverlay() }
        uiHandler.postDelayed(blackoutRunnable!!, 10_000)
    }

    private fun cancelBlackout() {
        blackoutRunnable?.let { uiHandler.removeCallbacks(it) }
        blackoutRunnable = null
        hideBlackoutOverlay()
    }

    private fun showBlackoutOverlay() {
        runOnUiThread {
            try {
                // Lower window brightness to near-zero for dimming (per-window only)
                val lp = window.attributes
                // save original if not already saved
                if (originalBrightness < 0f) originalBrightness = lp.screenBrightness
                lp.screenBrightness = 0.01f
                window.attributes = lp
            } catch (e: Exception) {
                // ignore
            }
            blackoutOverlay.visibility = View.VISIBLE
        }
    }

    private fun hideBlackoutOverlay() {
        runOnUiThread {
            blackoutOverlay.visibility = View.GONE
            try {
                val lp = window.attributes
                lp.screenBrightness = originalBrightness
                window.attributes = lp
            } catch (e: Exception) {
                // ignore
            }
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
    
    private fun loadPreferences() {
        currentBitrateMbps = prefs.getInt("bitrate", 15)
        currentWidth = prefs.getInt("width", 1920)
        currentHeight = prefs.getInt("height", 1080)
        currentFps = prefs.getInt("fps", 60)
    }
    
    private fun savePreferences() {
        prefs.edit().apply {
            putString("server_url", urlInput.text.toString())
            putInt("bitrate", currentBitrateMbps)
            putInt("width", currentWidth)
            putInt("height", currentHeight)
            putInt("fps", currentFps)
            apply()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isConnected) {
            // Disconnect instead of exiting when on preview page
            client.disconnect()
            isConnected = false
            cancelBlackout()
            showConnectionPage()
            statusText.text = "Disconnected"
        } else {
            savePreferences()
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    override fun onPause() {
        super.onPause()
        savePreferences()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cancelBlackout()
        client.stop()
        scope.cancel()
    }
}
