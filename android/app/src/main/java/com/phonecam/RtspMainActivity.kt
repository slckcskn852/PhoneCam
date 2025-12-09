package com.phonecam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Main Activity for RTSP streaming version
 * Streams camera directly to MediaMTX server over TCP/H.264
 */
class RtspMainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var client: RtspStreamer

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var preview: SurfaceView
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startCameraPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rtsp)
        
        // Keep screen on and prevent auto-lock
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide status bar and make fullscreen
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

        urlInput = findViewById(R.id.serverUrl)
        statusText = findViewById(R.id.statusText)
        preview = findViewById(R.id.preview)
        controlsLayout = findViewById(R.id.controlsLayout)
        darkOverlay = findViewById(R.id.darkOverlay)
        rootLayout = findViewById(R.id.rootLayout)
        connectBtn = findViewById(R.id.connectBtn)

        // Initialize RTSP streamer
        client = RtspStreamer(this) { status ->
            runOnUiThread {
                statusText.text = status
                if (status.contains("Streaming", ignoreCase = true)) {
                    isConnected = true
                    connectBtn.text = "Disconnect"
                    scheduleDimming()
                } else if (status.contains("Disconnected", ignoreCase = true) || 
                           status.contains("Error", ignoreCase = true) ||
                           status.contains("failed", ignoreCase = true)) {
                    isConnected = false
                    connectBtn.text = "Connect"
                    cancelDimming()
                    hideDarkOverlay()
                }
            }
        }

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
            startCameraPreview()
        } else {
            ensurePermissions()
        }

        connectBtn.setOnClickListener {
            if (isConnected) {
                // Disconnect
                client.disconnect()
                isConnected = false
                connectBtn.text = "Connect"
                statusText.text = "Disconnected"
                cancelDimming()
                hideDarkOverlay()
            } else {
                // Connect
                if (!hasPermissions()) {
                    statusText.text = "Camera permission required"
                    ensurePermissions()
                    return@setOnClickListener
                }
                
                val url = urlInput.text.toString().ifBlank {
                    getString(R.string.default_rtsp_url)
                }
                
                client.connect(url, this)
            }
        }
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val previewUseCase = Preview.Builder()
                .setTargetResolution(android.util.Size(1920, 1080))
                .build()
            
            previewUseCase.setSurfaceProvider { request ->
                val surface = preview.holder.surface
                if (surface != null && surface.isValid) {
                    request.provideSurface(surface, ContextCompat.getMainExecutor(this)) { }
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase)
                statusText.text = "Ready to connect"
            } catch (e: Exception) {
                statusText.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun scheduleDimming() {
        cancelDimming()
        dimHandler.postDelayed(dimRunnable, 5000)
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

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cancelDimming()
        client.stop()
        scope.cancel()
    }

    override fun onPause() {
        super.onPause()
        cancelDimming()
    }

    override fun onResume() {
        super.onResume()
        if (isConnected) {
            scheduleDimming()
        }
    }
}
