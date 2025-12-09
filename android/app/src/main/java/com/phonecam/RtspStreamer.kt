package com.phonecam

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Streams camera H.264 video over raw TCP to a PC receiver.
 * The PC receiver reads H.264 Annex B NAL units and pipes to virtual camera.
 * 
 * Protocol: Raw H.264 Annex B stream over TCP
 * - Each NAL unit prefixed with 0x00000001 start code
 * - SPS/PPS sent with each keyframe for decoder resilience
 * 
 * Features:
 * - Adjustable bitrate (5-100 Mbps)
 * - Zoom support via CameraX
 */
class RtspStreamer(
    private val context: Context,
    initialBitrateMbps: Int = 15,
    private val statusCallback: (String) -> Unit
) {
    companion object {
        private const val TAG = "RtspStreamer"
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        private const val FPS = 60
        private const val I_FRAME_INTERVAL = 1 // Keyframe every 1 second
    }

    private val encoderThread = HandlerThread("EncoderThread").apply { start() }
    private val encoderHandler = Handler(encoderThread.looper)
    private val networkThread = HandlerThread("NetworkThread").apply { start() }
    private val networkHandler = Handler(networkThread.looper)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var encoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    
    private val isStreaming = AtomicBoolean(false)
    private val frameCount = AtomicLong(0)
    private var streamStartTime = 0L
    
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    
    // Adjustable bitrate (in Mbps, capped at 30)
    private val currentBitrateMbps = AtomicInteger(initialBitrateMbps.coerceIn(5, 30))
    
    // Current zoom level
    private var currentZoom = 1.0f
    
    // Zoom state callback
    var onZoomStateChanged: ((minZoom: Float, maxZoom: Float, currentZoom: Float) -> Unit)? = null

    /**
     * Set bitrate in Mbps (5-30)
     * Note: Changes take effect on next connection
     */
    fun setBitrate(mbps: Int) {
        currentBitrateMbps.set(mbps.coerceIn(5, 30))
        Log.d(TAG, "Bitrate set to ${currentBitrateMbps.get()} Mbps")
    }
    
    /**
     * Set zoom level
     * Applied immediately to camera if streaming
     */
    fun setZoom(zoom: Float) {
        currentZoom = zoom
        camera?.cameraControl?.setZoomRatio(zoom)
        Log.d(TAG, "Zoom set to ${zoom}x")
    }
    
    /**
     * Get the camera instance for external zoom control
     */
    fun getCamera(): Camera? = camera

    fun connect(serverUrl: String, lifecycleOwner: LifecycleOwner) {
        if (isStreaming.get()) {
            statusCallback("Already streaming")
            return
        }

        statusCallback("Connecting...")
        
        networkHandler.post {
            try {
                // Parse URL: tcp://host:port or just host:port
                val cleaned = serverUrl
                    .replace("tcp://", "")
                    .replace("rtsp://", "")
                    .trim()
                
                val parts = cleaned.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 5000

                Log.d(TAG, "Connecting to $host:$port")
                statusCallback("Connecting to $host:$port...")
                
                socket = Socket(host, port).apply {
                    tcpNoDelay = true
                    sendBufferSize = 65536 // Smaller buffer for lower latency
                    soTimeout = 5000
                }
                outputStream = socket!!.getOutputStream()
                
                Log.d(TAG, "TCP connected, starting encoder")
                statusCallback("Connected, starting encoder...")
                
                // Start encoder on its thread
                encoderHandler.post {
                    startEncoder(lifecycleOwner)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                statusCallback("Connection failed: ${e.message}")
                disconnect()
            }
        }
    }

    private fun startEncoder(lifecycleOwner: LifecycleOwner) {
        try {
            val bitrate = currentBitrateMbps.get() * 1_000_000
            
            // Create H.264 encoder
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                
                // Low latency tuning
                try {
                    setInteger(MediaFormat.KEY_LATENCY, 0)
                    setInteger(MediaFormat.KEY_PRIORITY, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Some low-latency keys not supported")
                }
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoderInputSurface = createInputSurface()
                start()
            }

            Log.d(TAG, "Encoder created: ${WIDTH}x${HEIGHT}@${FPS}fps, ${currentBitrateMbps.get()}Mbps")
            
            isStreaming.set(true)
            frameCount.set(0)
            streamStartTime = System.currentTimeMillis()

            // Bind camera to encoder surface
            ContextCompat.getMainExecutor(context).execute {
                bindCameraToEncoder(lifecycleOwner)
            }

            // Start encoder output loop
            startEncoderOutputLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Encoder start failed", e)
            ContextCompat.getMainExecutor(context).execute {
                statusCallback("Encoder failed: ${e.message}")
            }
            disconnect()
        }
    }

    private fun bindCameraToEncoder(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                cameraProvider?.unbindAll()

                // Preview for encoder with 60fps target using Camera2 Interop
                val previewBuilder = Preview.Builder()
                    .setTargetResolution(Size(WIDTH, HEIGHT))
                
                // Use Camera2 Interop to request 60fps
                @Suppress("UnsafeOptInUsageError")
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(FPS, FPS)
                    )
                
                val encoderPreview = previewBuilder.build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                // Provide encoder surface to camera
                encoderPreview.setSurfaceProvider { request ->
                    encoderInputSurface?.let { surface ->
                        request.provideSurface(surface, cameraExecutor) { result ->
                            Log.d(TAG, "Encoder surface result: ${result.resultCode}")
                        }
                    }
                }

                // Bind preview to camera
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    encoderPreview
                )
                
                // Apply current zoom and observe zoom state
                camera?.cameraControl?.setZoomRatio(currentZoom)
                
                // Notify zoom state for UI
                camera?.cameraInfo?.zoomState?.observeForever { state ->
                    onZoomStateChanged?.invoke(state.minZoomRatio, state.maxZoomRatio, state.zoomRatio)
                }

                Log.d(TAG, "Camera bound to encoder")
                val mbps = currentBitrateMbps.get()
                statusCallback("Streaming 1080p60 @ ${mbps}Mbps")

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                statusCallback("Camera error: ${e.message}")
                disconnect()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startEncoderOutputLoop() {
        Thread({
            val bufferInfo = MediaCodec.BufferInfo()
            val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
            
            Log.d(TAG, "Encoder output loop started")

            while (isStreaming.get()) {
                try {
                    val enc = encoder ?: break
                    val outputIndex = enc.dequeueOutputBuffer(bufferInfo, 10000) // 10ms timeout

                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // Extract SPS/PPS from encoder format
                            val format = enc.outputFormat
                            Log.d(TAG, "Encoder format: $format")
                            
                            format.getByteBuffer("csd-0")?.let { buf ->
                                sps = ByteArray(buf.remaining())
                                buf.get(sps!!)
                                Log.d(TAG, "SPS extracted: ${sps!!.size} bytes")
                            }
                            
                            format.getByteBuffer("csd-1")?.let { buf ->
                                pps = ByteArray(buf.remaining())
                                buf.get(pps!!)
                                Log.d(TAG, "PPS extracted: ${pps!!.size} bytes")
                            }
                            
                            // Send initial config
                            sendConfigData(startCode)
                        }
                        
                        outputIndex >= 0 -> {
                            val outputBuffer = enc.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                // Skip codec config buffers (already handled above)
                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    val isKeyFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                                    
                                    // Get NAL data
                                    val nalData = ByteArray(bufferInfo.size)
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.get(nalData)
                                    
                                    // Send to network
                                    sendFrame(nalData, isKeyFrame, startCode)
                                    
                                    val count = frameCount.incrementAndGet()
                                    
                                    // Update status periodically
                                    if (count % 60 == 0L) {
                                        val elapsed = (System.currentTimeMillis() - streamStartTime) / 1000.0
                                        val fps = if (elapsed > 0) count / elapsed else 0.0
                                        val mbps = currentBitrateMbps.get()
                                        ContextCompat.getMainExecutor(context).execute {
                                            statusCallback("Streaming: ${String.format("%.1f", fps)} fps @ ${mbps}Mbps")
                                        }
                                    }
                                }
                            }
                            enc.releaseOutputBuffer(outputIndex, false)
                        }
                        
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available, continue
                        }
                    }
                } catch (e: IllegalStateException) {
                    if (isStreaming.get()) {
                        Log.e(TAG, "Encoder state error", e)
                    }
                    break
                } catch (e: Exception) {
                    if (isStreaming.get()) {
                        Log.e(TAG, "Encoder output error", e)
                        ContextCompat.getMainExecutor(context).execute {
                            statusCallback("Stream error: ${e.message}")
                        }
                    }
                    break
                }
            }
            
            Log.d(TAG, "Encoder output loop ended, frames sent: ${frameCount.get()}")
        }, "EncoderOutput").start()
    }

    private fun sendConfigData(startCode: ByteArray) {
        try {
            val out = outputStream ?: return
            
            synchronized(out) {
                sps?.let { data ->
                    out.write(startCode)
                    out.write(data)
                    Log.d(TAG, "SPS sent")
                }
                pps?.let { data ->
                    out.write(startCode)
                    out.write(data)
                    Log.d(TAG, "PPS sent")
                }
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send config", e)
        }
    }

    private fun sendFrame(nalData: ByteArray, isKeyFrame: Boolean, startCode: ByteArray) {
        try {
            val out = outputStream ?: return
            
            synchronized(out) {
                // For keyframes, prepend SPS/PPS for decoder resilience
                if (isKeyFrame) {
                    sps?.let { data ->
                        out.write(startCode)
                        out.write(data)
                    }
                    pps?.let { data ->
                        out.write(startCode)
                        out.write(data)
                    }
                }
                
                // Check if NAL already has start code
                val hasStartCode = nalData.size >= 4 && 
                    nalData[0] == 0x00.toByte() && 
                    nalData[1] == 0x00.toByte() && 
                    nalData[2] == 0x00.toByte() && 
                    nalData[3] == 0x01.toByte()
                
                if (!hasStartCode) {
                    out.write(startCode)
                }
                out.write(nalData)
                out.flush()
            }
        } catch (e: Exception) {
            throw e // Propagate to caller
        }
    }

    fun disconnect() {
        val wasStreaming = isStreaming.getAndSet(false)
        
        Log.d(TAG, "Disconnecting, was streaming: $wasStreaming")
        
        // Stop encoder
        encoderHandler.post {
            try {
                encoder?.stop()
                encoder?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Encoder stop error", e)
            }
            encoder = null
            
            encoderInputSurface?.release()
            encoderInputSurface = null
        }

        // Close network
        networkHandler.post {
            try {
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Network close error", e)
            }
            outputStream = null
            socket = null
        }

        // Unbind camera
        ContextCompat.getMainExecutor(context).execute {
            cameraProvider?.unbindAll()
            statusCallback("Disconnected")
        }
    }

    fun stop() {
        disconnect()
        
        encoderThread.quitSafely()
        networkThread.quitSafely()
        cameraExecutor.shutdown()
    }
}
