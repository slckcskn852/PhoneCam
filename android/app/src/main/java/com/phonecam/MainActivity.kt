package com.phonecam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
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
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eglBase: EglBase by lazy { EglBase.create() }
    private val signaling = Signaling()
    private lateinit var client: WebRtcClient

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var preview: SurfaceViewRenderer
    private lateinit var controlsLayout: View
    private lateinit var darkOverlay: View
    private lateinit var rootLayout: View
    private lateinit var connectBtn: Button

    private var isConnected = false
    private var isDarkOverlayVisible = false
    private val dimHandler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable {
        if (isConnected && !isDarkOverlayVisible) {
            showDarkOverlay()
        }
    }
    private var currentBitrateMbps = 15 // Fixed 15 Mbps bitrate cap

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            // All permissions granted, start preview
            scope.launch {
                try {
                    client.startPreview()
                    runOnUiThread { statusText.text = "Ready to connect" }
                } catch (t: Throwable) {
                    runOnUiThread { statusText.text = "Preview failed: ${t.message}" }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Keep screen on and prevent auto-lock
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide status bar and make fullscreen (must be after setContentView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
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

        urlInput = findViewById(R.id.signalingUrl)
        statusText = findViewById(R.id.statusText)
        preview = findViewById(R.id.preview)
        controlsLayout = findViewById(R.id.controlsLayout)
        darkOverlay = findViewById(R.id.darkOverlay)
        rootLayout = findViewById(R.id.rootLayout)
        connectBtn = findViewById(R.id.connectBtn)

        // Initialize preview renderer first
        preview.init(eglBase.eglBaseContext, null)
        preview.setEnableHardwareScaler(true)
        preview.setMirror(true)

        client = WebRtcClient(this, eglBase, signaling, currentBitrateMbps) { status ->
            runOnUiThread {
                statusText.text = status
                // Check if connected
                if (status.contains("Connected", ignoreCase = true)) {
                    isConnected = true
                    connectBtn.text = "Disconnect"
                    scheduleDimming()
                } else if (status.contains("Stopping", ignoreCase = true) || 
                           status.contains("Error", ignoreCase = true) ||
                           status.contains("Disconnected", ignoreCase = true)) {
                    isConnected = false
                    connectBtn.text = "Connect"
                    cancelDimming()
                    hideDarkOverlay()
                }
            }
        }
        client.attachPreview(preview)

        // Touch listener for dark overlay toggle
        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isConnected) {
                resetDimming()
                if (isDarkOverlayVisible) {
                    hideDarkOverlay()
                }
            }
            false
        }

        darkOverlay.setOnClickListener {
            hideDarkOverlay()
            resetDimming()
        }

        if (hasPermissions()) {
            // Start preview immediately if we already have permissions
            scope.launch {
                try {
                    client.startPreview()
                    runOnUiThread { statusText.text = "Ready to connect" }
                } catch (t: Throwable) {
                    runOnUiThread { statusText.text = "Preview failed: ${t.message}" }
                }
            }
        } else {
            ensurePermissions()
        }

        connectBtn.setOnClickListener {
            if (isConnected) {
                // Disconnect
                scope.launch {
                    try {
                        client.disconnect()
                        runOnUiThread {
                            isConnected = false
                            connectBtn.text = "Connect"
                            statusText.text = "Disconnected"
                            cancelDimming()
                            hideDarkOverlay()
                        }
                    } catch (t: Throwable) {
                        runOnUiThread {
                            statusText.text = "Disconnect error: ${t.message}"
                        }
                    }
                }
            } else {
                // Connect
                if (!hasPermissions()) {
                    statusText.text = "Camera/Mic permissions required"
                    ensurePermissions()
                    return@setOnClickListener
                }
                val url = urlInput.text.toString().ifBlank {
                    getString(R.string.default_signaling_url)
                }
                scope.launch {
                    try {
                        client.connect(url)
                    } catch (t: Throwable) {
                        runOnUiThread {
                            statusText.text = "Error: ${t.message}"
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDimming() {
        cancelDimming()
        dimHandler.postDelayed(dimRunnable, 5000) // 5 seconds
    }

    private fun cancelDimming() {
        dimHandler.removeCallbacks(dimRunnable)
    }

    private fun resetDimming() {
        cancelDimming()
        scheduleDimming()
    }

    private fun showDarkOverlay() {
        darkOverlay.visibility = View.VISIBLE
        controlsLayout.visibility = View.GONE
        isDarkOverlayVisible = true
    }

    private fun hideDarkOverlay() {
        darkOverlay.visibility = View.GONE
        controlsLayout.visibility = View.VISIBLE
        isDarkOverlayVisible = false
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.CAMERA
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release wake lock flag
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cancelDimming()
        client.stop()
        eglBase.release()
        scope.cancel()
        preview.release()
    }

    override fun onPause() {
        super.onPause()
        cancelDimming()
        // Camera might be lost when device locks
    }

    override fun onResume() {
        super.onResume()
        if (isConnected) {
            scheduleDimming()
            // Restart camera after unlock if connected
            scope.launch {
                try {
                    client.restartCamera()
                } catch (t: Throwable) {
                    runOnUiThread { 
                        statusText.text = "Camera restart failed: ${t.message}"
                    }
                }
            }
        }
    }
}
