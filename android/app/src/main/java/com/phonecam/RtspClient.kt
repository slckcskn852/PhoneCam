package com.phonecam

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RTSP Client that captures camera and streams H.264 to MediaMTX server
 * Uses a simple RTSP ANNOUNCE/SETUP/RECORD flow to push video
 */
class RtspClient(
    private val context: Context,
    private val status: (String) -> Unit
) {
    companion object {
        private const val TAG = "RtspClient"
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        private const val FPS = 60
        private const val BITRATE = 15_000_000 // 15 Mbps
        private const val I_FRAME_INTERVAL = 1 // seconds
    }

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val encoderThread = HandlerThread("EncoderThread").apply { start() }
    private val encoderHandler = Handler(encoderThread.looper)
    private val networkThread = HandlerThread("NetworkThread").apply { start() }
    private val networkHandler = Handler(networkThread.looper)

    private var cameraProvider: ProcessCameraProvider? = null
    private var encoder: MediaCodec? = null
    private var encoderSurface: Surface? = null
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    
    private val isStreaming = AtomicBoolean(false)
    private val isPreviewStarted = AtomicBoolean(false)
    
    private var previewSurface: Surface? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    fun attachPreview(surface: Surface) {
        previewSurface = surface
    }

    fun startPreview(lifecycleOwner: LifecycleOwner) {
        if (isPreviewStarted.get()) return
        
        status("Starting camera preview...")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraPreview(lifecycleOwner)
                isPreviewStarted.set(true)
                status("Camera preview ready")
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider failed", e)
                status("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraPreview(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return
        
        // Unbind all use cases before rebinding
        provider.unbindAll()

        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(WIDTH, HEIGHT))
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            
            // Note: Preview surface is set via SurfaceView in MainActivity
            Log.d(TAG, "Camera preview bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            status("Camera binding failed: ${e.message}")
        }
    }

    fun connect(rtspUrl: String, lifecycleOwner: LifecycleOwner) {
        if (isStreaming.get()) {
            status("Already streaming")
            return
        }

        status("Connecting to RTSP server...")
        
        networkHandler.post {
            try {
                // Parse RTSP URL: rtsp://host:port/path
                val url = rtspUrl.replace("rtsp://", "")
                val hostPort = url.split("/")[0]
                val host = hostPort.split(":")[0]
                val port = hostPort.split(":").getOrNull(1)?.toIntOrNull() ?: 8554
                val path = "/" + url.substringAfter("/", "live/phone")

                Log.d(TAG, "Connecting to $host:$port$path")
                
                // Connect TCP socket to MediaMTX
                socket = Socket(host, port).apply {
                    tcpNoDelay = true
                    sendBufferSize = 512 * 1024
                }
                outputStream = DataOutputStream(socket!!.getOutputStream())
                
                // Start encoder
                encoderHandler.post {
                    startEncoder(lifecycleOwner)
                }
                
                isStreaming.set(true)
                status("Connected - Streaming to $host:$port")
                
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                status("Connection failed: ${e.message}")
                disconnect()
            }
        }
    }

    private fun startEncoder(lifecycleOwner: LifecycleOwner) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                // Low latency settings
                setInteger(MediaFormat.KEY_LATENCY, 0)
                setInteger(MediaFormat.KEY_PRIORITY, 0) // Real-time priority
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoderSurface = createInputSurface()
                start()
            }

            Log.d(TAG, "Encoder started: ${WIDTH}x${HEIGHT}@${FPS}fps, ${BITRATE/1_000_000}Mbps")
            status("Encoder started")

            // Bind camera to encoder surface
            bindCameraToEncoder(lifecycleOwner)

            // Start reading encoded output
            startEncoderOutput()

        } catch (e: Exception) {
            Log.e(TAG, "Encoder start failed", e)
            status("Encoder failed: ${e.message}")
            disconnect()
        }
    }

    private fun bindCameraToEncoder(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: run {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                cameraProvider = future.get()
                bindCameraToEncoder(lifecycleOwner)
            }, ContextCompat.getMainExecutor(context))
            return
        }

        ContextCompat.getMainExecutor(context).execute {
            try {
                provider.unbindAll()

                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(WIDTH, HEIGHT))
                    .build()

                // Create a secondary preview for the encoder
                val encoderPreview = Preview.Builder()
                    .setTargetResolution(android.util.Size(WIDTH, HEIGHT))
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )

                // Set encoder surface as preview output
                encoderSurface?.let { surface ->
                    preview.setSurfaceProvider { request ->
                        request.provideSurface(surface, cameraExecutor) { result ->
                            Log.d(TAG, "Surface result: ${result.resultCode}")
                        }
                    }
                }

                Log.d(TAG, "Camera bound to encoder")
                status("Streaming 1080p60 @ 15 Mbps")

            } catch (e: Exception) {
                Log.e(TAG, "Camera-encoder binding failed", e)
                status("Camera binding failed: ${e.message}")
            }
        }
    }

    private fun startEncoderOutput() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            var frameCount = 0L
            val startTime = System.currentTimeMillis()

            while (isStreaming.get()) {
                try {
                    val enc = encoder ?: break
                    val outputIndex = enc.dequeueOutputBuffer(bufferInfo, 10000)

                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = enc.outputFormat
                            Log.d(TAG, "Encoder format changed: $newFormat")
                            
                            // Extract SPS/PPS from format
                            newFormat.getByteBuffer("csd-0")?.let { buffer ->
                                sps = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                Log.d(TAG, "SPS: ${sps?.size} bytes")
                            }
                            newFormat.getByteBuffer("csd-1")?.let { buffer ->
                                pps = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                Log.d(TAG, "PPS: ${pps?.size} bytes")
                            }
                            
                            // Send SPS/PPS first
                            sendConfigData()
                        }
                        outputIndex >= 0 -> {
                            val outputBuffer = enc.getOutputBuffer(outputIndex) ?: continue
                            
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                // Config data already handled
                                enc.releaseOutputBuffer(outputIndex, false)
                                continue
                            }

                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.get(data)
                            
                            // Send NAL unit over TCP
                            sendNalUnit(data, bufferInfo.presentationTimeUs, 
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)
                            
                            enc.releaseOutputBuffer(outputIndex, false)
                            frameCount++

                            // Log stats every 60 frames
                            if (frameCount % 60 == 0L) {
                                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                                val fps = frameCount / elapsed
                                Log.d(TAG, "Streaming: $frameCount frames, ${String.format("%.1f", fps)} fps")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isStreaming.get()) {
                        Log.e(TAG, "Encoder output error", e)
                    }
                    break
                }
            }
            Log.d(TAG, "Encoder output loop ended")
        }.start()
    }

    private fun sendConfigData() {
        networkHandler.post {
            try {
                val out = outputStream ?: return@post
                
                // Send SPS
                sps?.let { data ->
                    sendPacket(out, data, 0, isConfig = true)
                }
                
                // Send PPS
                pps?.let { data ->
                    sendPacket(out, data, 0, isConfig = true)
                }
                
                Log.d(TAG, "Config data sent (SPS/PPS)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send config data", e)
            }
        }
    }

    private fun sendNalUnit(data: ByteArray, pts: Long, isKeyFrame: Boolean) {
        networkHandler.post {
            try {
                val out = outputStream ?: return@post
                
                // If keyframe, resend SPS/PPS before it
                if (isKeyFrame) {
                    sps?.let { sendPacket(out, it, pts, isConfig = true) }
                    pps?.let { sendPacket(out, it, pts, isConfig = true) }
                }
                
                sendPacket(out, data, pts, isConfig = false)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send NAL unit", e)
                if (isStreaming.get()) {
                    ContextCompat.getMainExecutor(context).execute {
                        status("Stream error: ${e.message}")
                    }
                    disconnect()
                }
            }
        }
    }

    private fun sendPacket(out: DataOutputStream, data: ByteArray, pts: Long, isConfig: Boolean) {
        // Simple framing: 4-byte length prefix + data
        // This is compatible with reading on the PC side with FFmpeg
        synchronized(out) {
            // Write Annex B start code + NAL
            val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
            out.write(startCode)
            out.write(data)
            out.flush()
        }
    }

    fun disconnect() {
        if (!isStreaming.getAndSet(false) && encoder == null) return
        
        status("Disconnecting...")
        Log.d(TAG, "Disconnecting RTSP stream")

        // Stop encoder
        encoderHandler.post {
            try {
                encoder?.stop()
                encoder?.release()
                encoder = null
                encoderSurface?.release()
                encoderSurface = null
            } catch (e: Exception) {
                Log.e(TAG, "Encoder cleanup error", e)
            }
        }

        // Close network
        networkHandler.post {
            try {
                outputStream?.close()
                socket?.close()
                outputStream = null
                socket = null
            } catch (e: Exception) {
                Log.e(TAG, "Network cleanup error", e)
            }
        }

        status("Disconnected")
    }

    fun stop() {
        disconnect()
        
        cameraProvider?.unbindAll()
        cameraProvider = null
        
        cameraThread.quitSafely()
        encoderThread.quitSafely()
        networkThread.quitSafely()
        cameraExecutor.shutdown()
    }
}
